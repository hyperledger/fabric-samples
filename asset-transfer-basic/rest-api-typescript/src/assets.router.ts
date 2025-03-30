/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * This sample is intended to work with the basic asset transfer
 * chaincode which imposes some constraints on what is possible here.
 *
 * For example,
 *  - There is no validation for Asset IDs
 *  - There are no error codes from the chaincode
 *
 * To avoid timeouts, long running tasks should be decoupled from HTTP request
 * processing
 *
 * Submit transactions can potentially be very long running, especially if the
 * transaction fails and needs to be retried one or more times
 *
 * To allow requests to respond quickly enough, this sample queues submit
 * requests for processing asynchronously and immediately returns 202 Accepted
 */

import express, { Request, Response } from 'express';
import { body, validationResult } from 'express-validator';
import { Contract } from 'fabric-network';
import { getReasonPhrase, StatusCodes } from 'http-status-codes';
import { Queue } from 'bullmq';
import { AssetNotFoundError } from './errors';
import { evatuateTransaction } from './fabric';
import { addSubmitTransactionJob } from './jobs';
import { logger } from './logger';
import { createHash } from 'crypto';

const { ACCEPTED, BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK } =
    StatusCodes;

export const assetsRouter = express.Router();

assetsRouter.get('/', async (req: Request, res: Response) => {
    logger.debug('Get all assets request received');
    try {
        const mspId = req.user as string;
        const contract = req.app.locals[mspId]?.assetContract as Contract;

        const data = await evatuateTransaction(contract, 'GetAllAssets');
        let assets = [];
        if (data.length > 0) {
            assets = JSON.parse(data.toString());
        }

        return res.status(OK).json(assets);
    } catch (err) {
        logger.error({ err }, 'Error processing get all assets request');
        return res.status(INTERNAL_SERVER_ERROR).json({
            status: getReasonPhrase(INTERNAL_SERVER_ERROR),
            timestamp: new Date().toISOString(),
        });
    }
});
assetsRouter.post(
    '/student',
    body().isObject().withMessage('body must contain an asset object'),
    body('RollNo', 'must be a string').notEmpty(),
    body('Name', 'must be a string').notEmpty(),
    async (req: Request, res: Response) => {
        logger.debug(req.body, 'Create asset request received');

        const errors = validationResult(req);
        if (!errors.isEmpty()) {
            return res.status(BAD_REQUEST).json({
                status: getReasonPhrase(BAD_REQUEST),
                reason: 'VALIDATION_ERROR',
                message: 'Invalid request body',
                timestamp: new Date().toISOString(),
                errors: errors.array(),
            });
        }

        const mspId = req.user as string;
        const rollNo = req.body.RollNo;

        try {
            const submitQueue = req.app.locals.jobq as Queue;
            const jobId = await addSubmitTransactionJob(
                submitQueue,
                mspId,
                'CreateStudentData',
                rollNo,
                req.body.Name
            );

            return res.status(ACCEPTED).json({
                status: getReasonPhrase(ACCEPTED),
                jobId: jobId,
                timestamp: new Date().toISOString(),
            });
        } catch (err) {
            logger.error(
                { err },
                'Error processing create asset request for asset ID %s',
                rollNo
            );

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);

assetsRouter.get('/:assetId', async (req: Request, res: Response) => {
    const assetId = req.params.assetId;
    logger.debug('Read asset request received for asset ID %s', assetId);

    try {
        const mspId = req.user as string;
        const contract = req.app.locals[mspId]?.assetContract as Contract;

        const data = await evatuateTransaction(contract, 'ReadAsset', assetId);
        const asset = JSON.parse(data.toString());

        return res.status(OK).json(asset);
    } catch (err) {
        logger.error(
            { err },
            'Error processing read asset request for asset ID %s',
            assetId
        );

        if (err instanceof AssetNotFoundError) {
            return res.status(NOT_FOUND).json({
                status: getReasonPhrase(NOT_FOUND),
                timestamp: new Date().toISOString(),
            });
        }

        return res.status(INTERNAL_SERVER_ERROR).json({
            status: getReasonPhrase(INTERNAL_SERVER_ERROR),
            timestamp: new Date().toISOString(),
        });
    }
});

assetsRouter.delete('/:assetId', async (req: Request, res: Response) => {
    logger.debug(req.body, 'Delete asset request received');

    const mspId = req.user as string;
    const assetId = req.params.assetId;

    try {
        const submitQueue = req.app.locals.jobq as Queue;
        const jobId = await addSubmitTransactionJob(
            submitQueue,
            mspId,
            'DeleteAsset',
            assetId
        );

        return res.status(ACCEPTED).json({
            status: getReasonPhrase(ACCEPTED),
            jobId: jobId,
            timestamp: new Date().toISOString(),
        });
    } catch (err) {
        logger.error(
            { err },
            'Error processing delete asset request for asset ID %s',
            assetId
        );

        return res.status(INTERNAL_SERVER_ERROR).json({
            status: getReasonPhrase(INTERNAL_SERVER_ERROR),
            timestamp: new Date().toISOString(),
        });
    }
});

assetsRouter.post(
    '/dept',
    body().isObject().withMessage('body must contain an asset object'),
    body('DeptName', 'must be a string').notEmpty(),
    body('Year', 'must be a number').isNumeric(),
    async (req: Request, res: Response) => {
        logger.debug(req.body, 'Create asset request received');

        const errors = validationResult(req);
        if (!errors.isEmpty()) {
            return res.status(BAD_REQUEST).json({
                status: getReasonPhrase(BAD_REQUEST),
                reason: 'VALIDATION_ERROR',
                message: 'Invalid request body',
                timestamp: new Date().toISOString(),
                errors: errors.array(),
            });
        }

        const mspId = req.user as string;
        const assetId = req.body.DeptName;

        try {
            const submitQueue = req.app.locals.jobq as Queue;
            const jobId = await addSubmitTransactionJob(
                submitQueue,
                mspId,
                'CreateDept',
                assetId,
                req.body.Year
            );

            return res.status(ACCEPTED).json({
                status: getReasonPhrase(ACCEPTED),
                jobId: jobId,
                timestamp: new Date().toISOString(),
            });
        } catch (err) {
            logger.error(
                { err },
                'Error processing create asset request for asset ID %s',
                assetId
            );

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);

assetsRouter.post(
    '/sub',
    body().isObject().withMessage('body must contain an asset object'),
    body('DeptID', 'must be a string').notEmpty(),
    body('Subject', 'must be a string').notEmpty(),
    async (req: Request, res: Response) => {
        logger.debug(req.body, 'Create asset request received');

        const errors = validationResult(req);
        if (!errors.isEmpty()) {
            return res.status(BAD_REQUEST).json({
                status: getReasonPhrase(BAD_REQUEST),
                reason: 'VALIDATION_ERROR',
                message: 'Invalid request body',
                timestamp: new Date().toISOString(),
                errors: errors.array(),
            });
        }

        const mspId = req.user as string;
        const assetId = req.body.DeptID;

        try {
            const submitQueue = req.app.locals.jobq as Queue;
            const jobId = await addSubmitTransactionJob(
                submitQueue,
                mspId,
                'deptAddSubject',
                assetId,
                req.body.Subject
            );

            return res.status(ACCEPTED).json({
                status: getReasonPhrase(ACCEPTED),
                jobId: jobId,
                timestamp: new Date().toISOString(),
            });
        } catch (err) {
            logger.error(
                { err },
                'Error processing create asset request for asset ID %s',
                assetId
            );

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);

assetsRouter.post(
    '/addstu',
    body().isObject().withMessage('body must contain an asset object'),
    body('DeptID', 'must be a string').notEmpty(),
    body('RollNo', 'must be a string').notEmpty(),
    async (req: Request, res: Response) => {
        logger.debug(req.body, 'Create asset request received');

        const errors = validationResult(req);
        if (!errors.isEmpty()) {
            return res.status(BAD_REQUEST).json({
                status: getReasonPhrase(BAD_REQUEST),
                reason: 'VALIDATION_ERROR',
                message: 'Invalid request body',
                timestamp: new Date().toISOString(),
                errors: errors.array(),
            });
        }

        const mspId = req.user as string;
        const assetId = req.body.DeptID;

        try {
            const submitQueue = req.app.locals.jobq as Queue;
            const jobId = await addSubmitTransactionJob(
                submitQueue,
                mspId,
                'deptAddStudent',
                assetId,
                req.body.RollNo
            );

            return res.status(ACCEPTED).json({
                status: getReasonPhrase(ACCEPTED),
                jobId: jobId,
                timestamp: new Date().toISOString(),
            });
        } catch (err) {
            logger.error(
                { err },
                'Error processing create asset request for asset ID %s',
                assetId
            );

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);

assetsRouter.post(
    '/stuaddsub',
    body().isObject().withMessage('body must contain an asset object'),
    body('RollNo', 'must be a string').notEmpty(),
    body('Subject', 'must be a string').notEmpty(),
    body('Mark', 'must be a number').isNumeric(),
    async (req: Request, res: Response) => {
        logger.debug(req.body, 'Create asset request received');
        const errors = validationResult(req);
        if (!errors.isEmpty()) {
            return res.status(BAD_REQUEST).json({
                status: getReasonPhrase(BAD_REQUEST),
                reason: 'VALIDATION_ERROR',
                message: 'Invalid request body',
                timestamp: new Date().toISOString(),
                errors: errors.array(),
            });
        }

        const mspId = req.user as string;
        const assetId = req.body.RollNo;

        try {
            const submitQueue = req.app.locals.jobq as Queue;
            const jobId = await addSubmitTransactionJob(
                submitQueue,
                mspId,
                'addStudentMark',
                assetId,
                req.body.Subject,
                req.body.Mark
            );

            return res.status(ACCEPTED).json({
                status: getReasonPhrase(ACCEPTED),
                jobId: jobId,
                timestamp: new Date().toISOString(),
            });
        } catch (err) {
            logger.error(
                { err },
                'Error processing create asset request for asset ID %s',
                assetId
            );

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);

assetsRouter.get('/student/:assetId', async (req: Request, res: Response) => {
    const rollNo = req.params.assetId;
    logger.debug('Read asset request received for asset ID %s', rollNo);

    try {
        const mspId = req.user as string;
        const contract = req.app.locals[mspId]?.assetContract as Contract;

        const data = await evatuateTransaction(contract, 'getStudent', rollNo);
        const asset = JSON.parse(data.toString());

        return res.status(OK).json(asset);
    } catch (err) {
        logger.error(
            { err },
            'Error processing read asset request for asset ID %s',
            rollNo
        );

        if (err instanceof AssetNotFoundError) {
            return res.status(NOT_FOUND).json({
                status: getReasonPhrase(NOT_FOUND),
                timestamp: new Date().toISOString(),
            });
        }

        return res.status(INTERNAL_SERVER_ERROR).json({
            status: getReasonPhrase(INTERNAL_SERVER_ERROR),
            timestamp: new Date().toISOString(),
        });
    }
});

assetsRouter.post(
    '/certificate',
    body().isObject().withMessage('body must contain an asset object'),
    body('Name', 'must be a string').notEmpty(),
    body('Event', 'must be a string').notEmpty(),
    body('Links', 'must be a string').notEmpty(),
    async (req: Request, res: Response) => {
        logger.debug(req.body, 'Create asset request received');
        const errors = validationResult(req);
        if (!errors.isEmpty()) {
            return res.status(BAD_REQUEST).json({
                status: getReasonPhrase(BAD_REQUEST),
                reason: 'VALIDATION_ERROR',
                message: 'Invalid request body',
                timestamp: new Date().toISOString(),
                errors: errors.array(),
            });
        }

        const mspId = req.user as string;

        try {
            const submitQueue = req.app.locals.jobq as Queue;
            const jobId = await addSubmitTransactionJob(
                submitQueue,
                mspId,
                'addCertificate',
                req.body.Name,
                req.body.Event,
                req.body.Links
            );

            const hash = createHash('sha256');

            hash.update(req.body.Name + req.body.Event + req.body.Links);

            const hashStr: string = hash.digest('hex');

            return res.status(ACCEPTED).json({
                certificate_hash: hashStr,
                job_id: jobId,
            });
        } catch (err) {
            logger.error(
                { err },
                'Error processing create asset request for asset ID %s',
                req.body.Name
            );

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);

assetsRouter.get(
    '/certificate',
    body().isObject().withMessage('body must contain an asset object'),
    body('Hash', 'must be a string').notEmpty(),
    async (req: Request, res: Response) => {
        const hashStr = req.body.Hash;
        logger.debug('Read asset request received for asset ID %s', hashStr);

        try {
            const mspId = req.user as string;
            const contract = req.app.locals[mspId]?.assetContract as Contract;

            const data = await evatuateTransaction(
                contract,
                'validateCertificate',
                hashStr
            );
            const asset = JSON.parse(data.toString());

            return res.status(OK).json(asset);
        } catch (err) {
            logger.error(
                { err },
                'Error processing read asset request for asset ID %s',
                hashStr
            );

            if (err instanceof AssetNotFoundError) {
                return res.status(NOT_FOUND).json({
                    status: getReasonPhrase(NOT_FOUND),
                    timestamp: new Date().toISOString(),
                });
            }

            return res.status(INTERNAL_SERVER_ERROR).json({
                status: getReasonPhrase(INTERNAL_SERVER_ERROR),
                timestamp: new Date().toISOString(),
            });
        }
    }
);
