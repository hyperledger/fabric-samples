import { logger } from './logger';
import { HeaderAPIKeyStrategy } from 'passport-headerapikey';
import * as config from './config';
export const fabricAPIKeyStrategy: HeaderAPIKeyStrategy =
  new HeaderAPIKeyStrategy(
    { header: 'api-key', prefix: 'Api-Key ' },
    true,
    function (apikey, done) {
      const user: { org: string } = {
        org: '',
      };
      if (apikey === config.org1ApiKey) {
        user.org = 'Org1';
        logger.info('Organisation set to Org1');
        done(null, user);

        //todo
        //add org2 apikey check
      } else {
        logger.debug('APIKEY Mismatch');
        return done(null, false);
      }
    }
  );
