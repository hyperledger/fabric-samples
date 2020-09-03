FROM node:8.17.0

WORKDIR /usr/src/app

COPY package*.json ./

RUN npm install

COPY . .

CMD ["node", "blockEventListener.js"]
