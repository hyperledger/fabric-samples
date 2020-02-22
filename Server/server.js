const express = require('express')
const app = express()
const port = 3000

app.use(express.static('client/public'));
app.get('/menu', function (req, res) {
    res.sendFile('mainMenu.html', { root: './client/html' });
});
app.get('/createNewBlock', function (req, res) {
    res.sendFile('createNewBlock.html', { root: './client/html' });
});
app.get('/searchBlock', function (req, res) {
    res.sendFile('about.html', { root: './client/html' });
});
app.get('/results', function (req, res) {
    res.sendFile('searchResults.html', { root: './client/html' });
});

app.listen(port, () => console.log(`Example app listening on port ${port}!`))