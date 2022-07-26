const express = require('express')
const app = express()

const { FileSystemWallet, Gateway, Wallets, GatewayOptions, Wallet} = require('fabric-network');
const path = require('path');
const fs = require("fs");

const ccpPath = path.resolve(__dirname, '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com', 'connection-org1.json');

// CORS Origin
app.use(function (req, res, next) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE');
    res.setHeader('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization');
    res.setHeader('Access-Control-Allow-Credentials', true);
    next();
});

app.use(express.json());
app.get('/getAllValidations', async (req, res) => {
    try {
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));
        const walletPath = path.join(process.cwd(), 'wallet')
        const wallet =await  Wallets.newFileSystemWallet(walletPath)
        const identity = await wallet.get('appUser');
        if (!identity) return;
        const gateway = new Gateway();
        await gateway.connect(ccp, {
            wallet,identity:'appUser', discovery: {enabled: true, asLocalhost: true}
        });
        const network = await gateway.getNetwork('mychannel');
        const contract = network.getContract('djinn');
        const result = await contract.evaluateTransaction('queryAllValidations');
        res.json({status: true, validations: JSON.parse(result.toString())});
    } catch (err) {
        res.json({status: false, error: err});
    }
});
app.post('/addValidation', async (req, res) => {
    console.log("req")
    console.log(req.body.age)
    try {
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));
        const walletPath = path.join(process.cwd(), 'wallet')
        const wallet =await  Wallets.newFileSystemWallet(walletPath)
        const identity = await wallet.get('appUser');
        if (!identity) return;
        const gateway = new Gateway();
        await gateway.connect(ccp, {
            wallet,identity:'appUser', discovery: {enabled: true, asLocalhost: true}
        });
        const network = await gateway.getNetwork('mychannel');
        const contract = network.getContract('djinn');
        await contract.submitTransaction('validateAge', req.body.age.toString());
        res.json({status: true, validations: JSON.parse(result.toString())});
    } catch (err) {
        res.json({status: false, error: err});
    }
});

app.get('/getValidationById', async (req, res) => {
    console.log("req")
    console.log(req.body.id)
    if (!req.body.id) return  res.json({status: false, error: "id of validation required"});
    try {
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));
        const walletPath = path.join(process.cwd(), 'wallet')
        const wallet =await  Wallets.newFileSystemWallet(walletPath)
        const identity = await wallet.get('appUser');
        if (!identity) return;
        const gateway = new Gateway();
        await gateway.connect(ccp, {
            wallet,identity:'appUser', discovery: {enabled: true, asLocalhost: true}
        });
        const network = await gateway.getNetwork('mychannel');
        const contract = network.getContract('djinn');
       let result = await contract.submitTransaction('getValidationById', req.body.id.toString());
        res.json({status: true, validations: JSON.parse(result.toString())});
    } catch (err) {
        res.json({status: false, error: err});
    }
});
app.listen(3000, () => {
    console.log('REST Server listening on port 3000');
});
