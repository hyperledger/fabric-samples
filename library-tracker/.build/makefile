.PHONY: all

ROOT_DIR:=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

all: digibank magnetocorp 

digibank:
	cd ${ROOT_DIR}/../organization/$@/application-java; mvn clean compile package
	cd ${ROOT_DIR}/../organization/$@/contract-java; ./gradlew clean build shadowJar
	cd ${ROOT_DIR}/../organization/$@/application; npm install && npm run lint
	cd ${ROOT_DIR}/../organization/$@/contract; npm install && npm run lint

magnetocorp:
	cd ${ROOT_DIR}/../organization/$@/application-java; mvn clean compile package
	cd ${ROOT_DIR}/../organization/$@/contract-java; ./gradlew clean build shadowJar	
	cd ${ROOT_DIR}/../organization/$@/application; npm install && npm run lint
	cd ${ROOT_DIR}/../organization/$@/contract; npm install && npm run lint	