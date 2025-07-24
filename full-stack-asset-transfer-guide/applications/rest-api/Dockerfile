FROM node:22-alpine AS build
WORKDIR /app
COPY package.json /app
RUN npm install
COPY  . /app
CMD [ "npm","run","prod" ]
