.PHONY: generate generate-ts build

generate: generate-ts
	./gradlew build

generate-ts:
	cd NextManagerTennis_WebApp/frontend && npm run generate:proto

build:
	./gradlew build
