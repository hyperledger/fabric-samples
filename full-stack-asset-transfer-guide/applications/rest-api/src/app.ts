import * as express from 'express';
import * as bodyParser from 'body-parser';
import { Connection } from './connection';
import { AssetRouter } from './assets.router';
var cors = require('cors');

class App {
    public app: express.Application;
    public routes: AssetRouter = new AssetRouter();
    constructor() {
        new Connection().init();
        this.app = express();
        this.app.use(cors());
        this.config();
        this.routes.routes(this.app);
    }

    private config(): void {
        // support application/json type post data
        this.app.use(bodyParser.json());
        //support application/x-www-form-urlencoded post data
        this.app.use(bodyParser.urlencoded({
            extended: false
        }));
    }
}
export default new App().app;