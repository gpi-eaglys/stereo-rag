---
name: readable-tech-docs
description: Improve the readability of AI-generated documentation. Use when reviewing user docs or writing new docs. This skill aims to avoid docs that read like default LLM output (bloated, over-hedged, generically structured).
---

# Readable Technical Documentation

This document explains the required, formal wording to use in the project documentation and comments. 
Documentation must be short, explicit, precise and technical. 
Technical documentation is not an essay, not a journal or a novel. Technical documentation must NOT be funny, lyric or epic. It must be short, dry and precise.


## Audience
Technical documentation is compiled for programmers and engineers, with general technical and IT background. 
The reader may not be deeply familiar with all the technical concepts and details of the project. 

## Where to apply
Follow these rules in document strings, in source comments, and in markdown documentation files.


## When to apply
Apply these rules when creating new documentation. 
For existing documentation, apply these rules only when the user asks for a review or a rewrite.

## Rules 

1. **Simple English**: Use simple language, short words. 
  - Do not use sophisticated words and phrasing that normal people would not use. 
  - Keep using technical terms even if they are long
   

2. **Brevity** : Documentation must use short sentences. 
  - Do not write bloated, overlong sentences. 
  - No one can read dense paragraphs. Split sentences if they are too long. 
  - Avoid subordinate clauses. 
  - Avoid unnecessary adjectives and adverbs. 
    - Bad example:  "the script always echos the result" -> "always" is unnecessary   
    - Good example:  "the script prints the result to STDOUT"

3. **Technical terms**: use conventional technical terms. 
  - Do not use synonyms and metaphors. Don't try to sound funny or cool. Try to sound dry. 
    - Bad example:  "the guardian process watches over the workers" 
    - Good example:  "the supervisor process monitors the worker processes"

4. **No figurative speech** 
  - avoid metaphors, similes and other figures of speech
  - use standard, simple phrasing
  - say exactly what you want to say:
     - Bad example:  "the pubkey lands inside the verity-hashed region"   -> don't use "land"
     - Good example:  "the pubkey is copied inside the verity-hashed region" 

5. **Forbidden terms** : Avoid the following terms. They sound sloppy and unprofessional.
  - "land" -> DO NOT USE 
    - use: "results in", "copied to", "moved to" .. etc. 
    - Always use the appropriate technical term "land" would refer to.
  - "slate" (as a verb) -> DO NOT USE
    - use "delete" or "remove"
  - "works" (ambiguous) -> DO NOT USE 
    - prefer precise description, e.g.:
    - "succeeds" / "fails" / "returns exit code N" / "produces FILE"
  - "pivot" — DO NOT USE. 
  - "kill condition" -> DO NOT USE
  - "moat" -> DO NOT USE
  - "flip the trust" -> DO NOT USE
    - Describe the actual mechanism instead of the metaphor.

6. **One word per concept, forever**: Repetition is required. 
  - Use the same word for the same concept. Variation is bad. Variation is difficult to read. 

7. **No history**: Do not explain the history of the project in the docs.
  - The reader is not aware of the history of this project. The reader does not know how things were before the current situation. 
  - In the documentation, especially in source code documentation, do not refer how issues were before fixing them. Or what bug triggered a certain mechanism.
  - Refer only to the current situation
  - Refer only to a bug or previous issues, if it is absolutely needed to understand the current mechanism.
    - Bad example:  "We used to load the key at boot, but this caused race conditions, so now we load it after mount."
    - Good example:  "The key is loaded after mount."

8. **Declarative verbs** : Use declarative style. Explain what a script or function actually does. 
  - In docstrings and in documentation use declarative style. Explain what the components do. There is no rationale to use imperatives.
    - Bad example(docstring):  "Sort the values in the list and return the highest one"   -> imperative, DO NOT USE
    - Good example(docstring): "Sorts the values in the list and returns the highest one"  -> declarative  

9. **No attitude**: Stay calm, void of emotions, professional. 
  - Do not try to be funny -> DONT! You just sounds miserable. 
  - Do not try to be cool and stylish -> DONT! Your stylish, cool phrasing implies that you have no idea what you are talking about. 
    - Bad example:  "Boom! The key is unsealed and we're off to the races."
    - Good example:  "The key is unsealed. Boot continues."


