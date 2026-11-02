# Domain docs

This is a single-context repository. Before work that depends on domain terms, read root `GLOSSARY.md`. Before changing an area covered by an ADR, read the relevant files in `docs/adr/`. If these files do not exist yet, proceed; create them when the terms or decisions are ready to record.

Use terms defined in `GLOSSARY.md` in issues, proposals, hypotheses, and tests. Keep the glossary limited to domain terms; record implementation decisions in ADRs when appropriate. If a needed term is missing, consider whether the model needs updating. Surface conflicts with an existing ADR explicitly.

Expected layout:

```text
/
├── GLOSSARY.md
└── docs/adr/
    └── 0001-example-decision.md
```
