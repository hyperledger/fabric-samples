const  FabricSampleService = require("./invoker.js");

module.exports = async function(app){
  let fss = new FabricSampleService()
  await fss.init()

  app.get('/get_produce/:id', async function(req, res){
    console.log(req.params)
    asset = await fss.get_fabric(req.params.id);

    res.json(asset)
  });
  app.get('/add_produce/:produce', async function(req, res){
    p = req.params.produce
    pparts = p.split('-')
    result = await fss.add_fabric({ID: pparts[0], Color: pparts[1], Size: pparts[2], AppraisedValue:pparts[3], Owner: pparts[4]});
    res.json(result)
  });
  app.get('/get_all_produce', async function(req, res){
    res.json(await fss.get_all_fabric())  
  });
  app.get('/change_holder/:holder', async function(req, res){
    p = req.params.holder
    pparts = p.split('-')
    res.json(await fss.change_owner(pparts[0], pparts[1]));
  });
}
