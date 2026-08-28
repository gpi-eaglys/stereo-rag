"""
Wrapper of Silero VAD using Onnx runtime - without torch.
          cf.  https://github.com/snakers4/silero-vad
Torch version: https://github.com/snakers4/silero-vad/blob/master/utils_vad.py

This is a modified, streaming version of the Silero VAD.
More parameters, more complicated.
"""
import wave
import os
import collections
from enum import Enum
from typing import Generator, List, Union, Callable, Optional
import onnxruntime
import numpy as np

from axon.ai.services.api.vad import VadSegment, AbstractVad, VadLabel, AbstractVadStreamIterator
from axon.ai.services.vad.silero import MDL_DIR

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


class VadStreamState(Enum):
    """
    -------|???????*****|?????????-------
    OFF     warmup  ON   cooldown   OFF
           ^^^^^^^^^^^^^^^^^^^^^^^
           VAD span
    """
    OFF = 0
    WARMUP = 1
    ON = 2
    COOLDOWN = 3


class VadParams:
    def __init__(self, thresh: float, buff_size_byte: int, buff_size_ms: int):
        """
        :param thresh:
        :param buff_size_byte: buffer size in frames
        """
        self.thresh = thresh
        self.min_warmup = 2  # in buff cnt
        self.min_cooldown = 2  # in buff cnt
        self.lhs_pad = 2  # in buff cnt
        self.rhs_pad = 2  # in ms
        self.vad_buff_size = buff_size_byte  # size in frames
        self.vad_buff_size_ms = buff_size_ms


class VadCtx:
    def __init__(self, par_min_warmup: int, par_lhs_pad: int, par_min_cooldown: int):
        self.state: VadStreamState = VadStreamState.OFF
        self.buff = b''
        self.off = 0  # offset
        self.n_hit = 0
        self.n_cooldown = 0
        # Ring-buffer size:
        # At least lhs-pad ms on the left side.
        # Minimum speech length
        self.ring = collections.deque(maxlen=par_min_warmup + par_lhs_pad + par_min_cooldown + 1)
        # self.ring = collections.deque(maxlen=500)
        self.speech = []


class SileroStreamVad(AbstractVad):
    @staticmethod
    def _pack_segment(speech: List, buff_size_in_ms: int) -> VadSegment:
        data = b''.join([s[3] for s in speech])
        ms_tot = len(speech) * buff_size_in_ms
        start_ms = speech[0][0] * buff_size_in_ms
        return VadSegment(data=data, offset=start_ms / 1000, dur=ms_tot / 1000)

    def __init__(self, model: str = None, thresh: float = 0.4, hz: int = 16000):
        """
        :model: path to Silero model
        """
        if model is None:
            self._fpath_mdl = os.path.join(MDL_DIR, "silero-vad_v4.onnx")
            # self._fpath_mdl = os.path.join(SCRIPT_DIR, "mdl", "silero_vad.onnx")
        else:
            self._fpath_mdl = model

        if not os.path.isfile(self._fpath_mdl):
            raise ValueError(f"Cannot find Silero VAD model: '{self._fpath_mdl}'")

        opts = onnxruntime.SessionOptions()
        opts.inter_op_num_threads = 1
        opts.intra_op_num_threads = 1

        self._sess = onnxruntime.InferenceSession(self._fpath_mdl)
        self._hz: int = hz
        self._bit = 2
        self._buff_size_in_ms = 32  # 30
        self._buff_size = int(self._bit * self._buff_size_in_ms * (self._hz / 1000))  # buff size in frames
        self._buff_size_in_frame = int(self._buff_size / self._bit)
        batch_size = 1
        self._h = np.zeros((2, batch_size, 64)).astype('float32')  # 'h' hidden
        self._c = np.zeros((2, batch_size, 64)).astype('float32')  # 'c' context
        self._sr = np.array(self._hz, dtype='int64')  # 'sr': sampling rate
        self.par = VadParams(thresh=thresh, buff_size_byte=self._buff_size, buff_size_ms=self._buff_size_in_ms)
        # self.set_hz(16000)  # default sampling rate

    def set_hz(self, hz: int) -> None:
        if self._hz == int(hz):
            return
        self._hz = hz
        self._buff_size = int(self._bit * self._buff_size_in_ms * (self._hz / 1000))  # buff size in frames
        self._buff_size_in_frame = int(self._buff_size / self._bit)
        self._sr = np.array(self._hz, dtype='int64')  # 'sr': sampling rate
        self.par = VadParams(thresh=self.par.thresh, buff_size_byte=self._buff_size, buff_size_ms=self._buff_size_in_ms)

    def reset(self) -> None:
        self._reset_states()

    def _reset_states(self, batch_size: int = 1) -> None:
        self._h = np.zeros((2, batch_size, 64)).astype('float32')
        self._c = np.zeros((2, batch_size, 64)).astype('float32')
        self._sr = np.array(self._hz, dtype='int64')

    def _forward(self, buff: bytes) -> float:
        # inference on buffer
        int16 = np.frombuffer(buff, np.int16)
        f32 = int16.astype('float32') / 32768
        wave_in = np.expand_dims(f32, axis=0)
        if wave_in.shape[1] != self._buff_size_in_frame:
            # np.pad(wave_in, ((0, 0), (0, self._buff_size - wave_in.shape[1])))
            out, self._h, self._c = self._sess.run(None, {
                'input': np.pad(wave_in, ((0, 0), (0, self._buff_size_in_frame - wave_in.shape[1]))),
                'sr': self._sr, 'h': self._h, 'c': self._c})
        else:
            out, self._h, self._c = self._sess.run(None, {
                'input': wave_in, 'sr': self._sr, 'h': self._h, 'c': self._c})
        return out[0][0]

    @staticmethod
    def _post_proc(prob: float, ctx: VadCtx, par: VadParams) -> Optional[VadSegment]:
        ctx.off += 1
        span: Union[VadSegment, None] = None
        if prob > par.thresh:
            ctx.n_hit += 1
            ctx.n_cooldown = 0
            if ctx.state == VadStreamState.OFF:
                ctx.state = VadStreamState.WARMUP
            elif ctx.state == VadStreamState.WARMUP:
                if ctx.n_hit > par.min_warmup:
                    ctx.state = VadStreamState.ON
                    ix = ctx.n_hit + par.lhs_pad - 1  # -1: as curr frame not added to the ring yet
                    ix = min(len(ctx.ring), ix)  # avoid index error
                    for i in range(-ix, 0):  # ...(-ix, .., -1)
                        ctx.speech.append(ctx.ring[i])
                    ctx.speech.append((ctx.off, ctx.state.name, prob, ctx.buff))
            elif ctx.state == VadStreamState.ON:
                ctx.speech.append((ctx.off, ctx.state.name, prob, ctx.buff))
            elif ctx.state == VadStreamState.COOLDOWN:
                ctx.state = VadStreamState.ON
                ctx.speech.append((ctx.off, ctx.state.name, prob, ctx.buff))
        else:
            ctx.n_hit = 0
            if ctx.state == VadStreamState.ON:
                ctx.n_cooldown += 1
                ctx.state = VadStreamState.COOLDOWN
                ctx.speech.append((ctx.off, ctx.state.name, prob, ctx.buff))
            elif ctx.state == VadStreamState.WARMUP:
                ctx.state = VadStreamState.OFF
            elif ctx.state == VadStreamState.COOLDOWN:
                if ctx.n_cooldown >= par.min_cooldown:
                    ctx.state = VadStreamState.OFF
                    ctx.n_cooldown = 0
                    # buff_size_in_ms = int(par.vad_buff_size / 2 / 16)  # 16 -> 16000 / 1000 simplified
                    span = SileroStreamVad._pack_segment(speech=ctx.speech, buff_size_in_ms=par.vad_buff_size_ms)
                    ctx.speech.clear()
                else:
                    ctx.speech.append((ctx.off, ctx.state.name, prob, ctx.buff))
                    ctx.n_cooldown += 1
        ctx.ring.append((ctx.off, ctx.state.name, prob, ctx.buff))
        # print("{}\t{:8.3f}\t{}\t{}".format(ctx.off, prob, ctx.state, ctx.n_hit))
        return span

    def segment_file(self, path: str) -> Generator[VadSegment, None, None]:
        self._reset_states()
        ctx = VadCtx(self.par.min_warmup, self.par.lhs_pad, self.par.min_cooldown)
        fh = wave.open(path, "rb")
        ctx.buff = fh.readframes(self._buff_size_in_frame)
        while len(ctx.buff) > 0:
            prob: float = self._forward(ctx.buff)
            span = self._post_proc(prob, ctx, self.par)
            if span is not None:
                yield span
            ctx.buff = fh.readframes(self._buff_size_in_frame)
        fh.close()

    def label_audio_file(self, path: str) -> Generator[VadLabel, None, None]:
        """
        Labels input audio
        :param path: path to PCM audio file. Must be 16kHz
        :return:
        """
        self._reset_states()
        ctx = VadCtx(self.par.min_warmup, self.par.lhs_pad, self.par.min_cooldown)
        fh = wave.open(path, "rb")
        self.set_hz(fh.getframerate())
        ctx.buff = fh.readframes(self._buff_size_in_frame)
        tot_bytes = len(ctx.buff)
        while len(ctx.buff) > 0:
            prob: float = self._forward(ctx.buff)
            # print("{}\t{}".format(tot_bytes / (self._bit * self._hz), prob))
            span = self._post_proc(prob, ctx, self.par)
            if span is not None:
                yield VadLabel(span.offset, span.dur, "SPEECH")
            ctx.buff = fh.readframes(self._buff_size_in_frame)
            tot_bytes += len(ctx.buff)
        fh.close()

        # handle last frame
        if ctx.state in [VadStreamState.COOLDOWN, VadStreamState.ON]:
            buff_size_in_ms = int(self.par.vad_buff_size / self._bit / 16)  # 16 -> 16000 / 1000 simplified
            span = SileroStreamVad._pack_segment(speech=ctx.speech, buff_size_in_ms=buff_size_in_ms)
            yield VadLabel(span.offset, span.dur, "SPEECH")

    class StreamIterator(AbstractVadStreamIterator):
        """
        Internal class that enables feeding byte buffers to the VAD
        """
        def __init__(self, ctx: VadCtx, vad_buff_size: int, vad_params: VadParams, forward_fun: Callable):
            self.ctx = ctx
            self.vad_buff_byte_size = vad_buff_size
            self.par = vad_params
            self._forward = forward_fun
            self.tot_off = 0

        def add_buff(self, buff: bytes) -> List[VadSegment]:
            off = 0
            to_copy = min(len(buff), self.vad_buff_byte_size - len(self.ctx.buff))
            spans: List = []
            while to_copy > 0:
                self.ctx.buff = self.ctx.buff + buff[off:(off + to_copy)]
                off += to_copy
                self.tot_off += to_copy
                # data crunching
                if len(self.ctx.buff) == self.vad_buff_byte_size:
                    prob: float = self._forward(self.ctx.buff)
                    span = SileroStreamVad._post_proc(prob, self.ctx, self.par)
                    if span is not None:
                        yield span
                    self.ctx.buff = b''
                to_copy = min(self.vad_buff_byte_size, len(buff) - off)

            # carry over remaining
            self.ctx.buff += buff[off:]
            return spans

        def end(self) -> VadSegment:
            span = None
            if len(self.ctx.buff) > 0:
                self.ctx.buff.ljust(self.vad_buff_byte_size, b'\0')  # pad to size
                prob: float = self._forward(self.ctx.buff)
                span = SileroStreamVad._post_proc(prob, self.ctx, self.par)
            return span

    def segment_stream(self) -> StreamIterator:
        """
        Returns a stream iterator that can be fed with byte buffers.

        @return: a StreamIterator object
        """
        self._reset_states()
        ctx = VadCtx(self.par.min_warmup, self.par.lhs_pad, self.par.min_cooldown)
        buff_size = 2 * self._buff_size_in_ms * 16
        return SileroStreamVad.StreamIterator(ctx=ctx, vad_buff_size=buff_size, vad_params=self.par,
                                              forward_fun=self._forward)
