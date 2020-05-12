/*
 *  * SPDX-License-Identifier: Apache-2.0
 *   */

'use strict';

const { Contract } = require('fabric-contract-api');

class Performance extends Contract {

	    async createPerformance(ctx, performanceID, artist, title) {
		            console.info('============= START : Create Performance ===========');

		            const performance = {
				              performanceID,
				              docType: 'performance',
				              artist,
				              title
				            };

		            await ctx.stub.putState(performanceID, Buffer.from(JSON.stringify(performance)));
		            console.info('============= END : Create Performance ===========');
		            }

	    async queryPerformance(ctx, performanceID) {
		            console.info('============= START : Query Performance ===========');
		            const performanceAsBytes = await ctx.stub.getState(performanceID); // get the car from chaincode state
		            if (!performanceAsBytes || performanceAsBytes.length === 0) {
				                throw new Error(`${performanceID} does not exist`);
				            }
		            console.log(performanceAsBytes.toString());
		            return performanceAsBytes.toString();
		            console.info('============= End : Query Performance ===========');
		        }
}

class License extends Contract {

	    async createLicense(ctx, licenseID, performanceID, license_recipient, type, expiration) {
		            console.info('============= START : Create License ===========');

		            const license = {
				                licenseID,
				                docType: 'license',
				                performanceID,
				                license_recipient,
				                type,
				                expiration,
				            };

		            await ctx.stub.putState(licenseID, Buffer.from(JSON.stringify(licenseID)));
		            console.info('============= END : Create License ===========');
		          }

	    async queryLicense(ctx, licenseID) {
		            console.info('============= START : Query License ===========');
		            const licenseAsBytes = await ctx.stub.getState(licenseID);
		            if (!licenseAsBytes || licenseAsBytes.length === 0) {
				                throw new Error(`${licenseID} does not exist`);
				            }
		            console.log(licenseAsBytes.toString());
		            return licenseAsBytes.toString();
		            console.info('============= END : Query License ===========');
		        }

	    async changeLicenseExpiration(ctx, licenseID, newExpiration) {
		            console.info('============= Begin : Change License Expiration ===========');
		            const carAsBytes = await ctx.stub.getState(carNumber); // get the car from chaincode state
		            if (!carAsBytes || carAsBytes.length === 0) {
				              throw new Error(`${carNumber} does not exist`);
				            }
		            const car = JSON.parse(carAsBytes.toString());
		            car.owner = newOwner;

		            await ctx.stub.putState(carNumber, Buffer.from(JSON.stringify(car)));
		            console.info('============= END : Change License Expiration ===========');
		        }

}

module.exports.Performance = Performance;
module.exports.License = Liscense;

