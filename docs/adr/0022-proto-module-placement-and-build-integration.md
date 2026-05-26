# ADR-0022 — Proto module placement and build integration

Le fichier `.proto` (contrat du simulateur, ADR-0019) vit dans `shared/proto/` à la racine du monorepo, déclaré comme sous-projet Gradle (`:shared:proto`). Il génère les stubs Java une seule fois ; `:simulator` et `:webapp:backend` déclarent `implementation(project(":shared:proto"))`. Le codegen TypeScript (ts-proto via grpc-tools) est déclenché par un script npm dans `NextManagerTennis_WebApp/frontend/`. Le code généré n'est jamais commité — il est produit au build. Un Makefile racine sert de point d'entrée unique (`make generate`) pour enchaîner le codegen TypeScript et `./gradlew build`.

## Considered Options

**Sous-module Git séparé** — rejeté. Le seul avantage d'un sous-module est le versionnage indépendant du contrat, utile quand plusieurs repos distincts consomment le `.proto`. Ce projet est un monorepo fermé avec deux consommateurs co-localisés — la complexité Git n'est pas justifiée.

**Répertoire brut (sans sous-projet Gradle)** — rejeté. Si chaque sous-projet Java configurait son propre plugin `protobuf` en pointant vers `shared/proto/`, les stubs Java seraient générés deux fois indépendamment. Un sous-projet Gradle centralise la génération et garantit que les deux consommateurs Java compilent contre les mêmes stubs.

**Gradle `Exec` task pour le codegen TypeScript** — rejeté. Coupler Gradle à npm suppose que `node` est disponible dans l'environnement Gradle et complique le build Java pour un gain marginal. Le Makefile sépare proprement les deux systèmes de build sans les fusionner.

## Consequences

- `settings.gradle.kts` à la racine inclut `:shared:proto`, `:simulator`, `:webapp:backend` avec `projectDir` explicite pour les dossiers à nommage long.
- `NextManagerTennis_WebApp/frontend/package.json` déclare `grpc-tools` et `ts-proto` en `devDependencies` et expose un script `generate:proto`.
- Tout changement de `.proto` exige : (1) `make generate` pour régénérer TypeScript, (2) `./gradlew build` pour recompiler Java (ou `make generate` qui enchaîne les deux).
- Le `.gitignore` exclut les dossiers de sortie du codegen (`build/generated/`, `src/generated/`).
