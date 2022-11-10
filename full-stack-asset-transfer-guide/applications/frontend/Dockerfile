# FROM node:14-alpine3.14 AS build
# WORKDIR /app
# COPY package.json /app
# RUN npm install
# CMD npm run build

# COPY  . /app
# CMD [ "npm","run","prod" ]

FROM nginx:alpine
COPY /dist/frontend /usr/share/nginx/html