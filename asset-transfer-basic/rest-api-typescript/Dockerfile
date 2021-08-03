FROM node:14-alpine3.12
RUN apk add dumb-init
WORKDIR /fabric_app/

COPY --chown=node:node . /fabric_app/

RUN npm ci 

RUN npm run build

EXPOSE 3000  

USER node
CMD dumb-init npm run start:dev






