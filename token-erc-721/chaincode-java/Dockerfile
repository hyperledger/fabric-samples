# the first stage 
FROM gradle:jdk11 AS GRADLE_BUILD

# copy the build.gradle and src code to the container
COPY src/ src/
COPY build.gradle ./ 

# Build and package our code
RUN gradle --no-daemon build shadowJar -x checkstyleMain -x checkstyleTest

# the second stage of our build just needs the compiled files
FROM openjdk:11-jre
ARG CC_SERVER_PORT=9999

# Setup tini to work better handle signals
ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini

RUN addgroup --system javauser && useradd -g javauser javauser

# copy only the artifacts we need from the first stage and discard the rest
COPY --chown=javauser:javauser --from=GRADLE_BUILD /home/gradle/build/libs/chaincode.jar /chaincode.jar
COPY --chown=javauser:javauser docker/docker-entrypoint.sh /docker-entrypoint.sh 
RUN chmod +x /docker-entrypoint.sh
ENV PORT $CC_SERVER_PORT
EXPOSE $CC_SERVER_PORT

USER javauser
ENTRYPOINT [ "/tini", "--", "/docker-entrypoint.sh" ]
