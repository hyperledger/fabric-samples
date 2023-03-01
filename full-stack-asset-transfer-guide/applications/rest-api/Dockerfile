FROM node:14-alpine3.14 AS build
WORKDIR /app
COPY package.json /app
RUN npm install
COPY  . /app
CMD [ "npm","run","prod" ]