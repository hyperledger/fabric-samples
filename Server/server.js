const express = require('express')
const app = express()
const port = 3000

app.use(express.static('Client/Public'));
app.get('/menu', function (req, res) {
    res.sendFile('title.html', { root: './Client/Views' });
});
app.get('/createNewBlock', function (req, res) {
    res.sendFile('game.html', { root: './client/Views' });
});
app.get('/searchBlock', function (req, res) {
    res.sendFile('about.html', { root: './client/Views' });
});
app.get('/results', function (req, res) {
    res.sendFile('menu.html', { root: './client/Views' });
});

app.listen(port, () => console.log(`Example app listening on port ${port}!`))