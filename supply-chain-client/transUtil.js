const FabricSampleService = require("./invoker.js");
let fss = new FabricSampleService()
fss.init().then(() => {
    //create asset 
    fss.add_fabric({ ID: "asset40", Color: "Red", Size: 100, AppraisedValue: 500, Owner: "GK" })
        .then((r1) => {
            console.log("Created asset:" + r1)
            fss.add_fabric({ ID: "asset41", Color: "Blue", Size: 10, AppraisedValue: 100, Owner: "Appu" })
                .then((r2) => {
                    console.log("Created asset:" + r2)
                    fss.add_fabric({ ID: "asset42", Color: "Blue", Size: 10, AppraisedValue: 100, Owner: "Appu" })
                        .then((r3) => {
                            console.log("Created asset:" + r3)
                            fss.change_owner("asset40", "Appu")
                        }).then(() => {
                            fss.change_owner("asset41", "Devu")
                        }).then(() => {
                            fss.change_owner("asset42", "Malu")
                        })
                })
        })
})