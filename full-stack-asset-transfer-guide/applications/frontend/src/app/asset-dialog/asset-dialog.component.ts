import { Component, Inject, OnInit } from '@angular/core';
import { FormGroup, FormControl } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Data } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { URLS } from '../urls';

@Component({
  selector: 'app-asset-dialog',
  templateUrl: './asset-dialog.component.html',
  styleUrls: ['./asset-dialog.component.scss']
})
export class AssetDialogComponent implements OnInit {
  stores = [];
  isEdit: Boolean = false;
  aseet= { "Color": "", "AppraisedValue": "", "ID": "", "Size": "", "Owner": "" };
  buttonText = "Save";
  form = new FormGroup({
    Color: new FormControl(''),
    AppraisedValue: new FormControl(''),
    Owner: new FormControl(''),
    Size: new FormControl('')
  });
  httpOptions: any;
  data:any;
  constructor(public dialogRef: MatDialogRef<any, any>,
    @Inject(MAT_DIALOG_DATA) public dialogdata: Data, private snackBar: MatSnackBar, private _http: HttpClient) {
    console.log(dialogdata);
    this.data=dialogdata;
    if (dialogdata) {
      var data = dialogdata;
      this.aseet.AppraisedValue = data['AppraisedValue'];
      this.aseet.Color = data['Color'];
      this.aseet.Size = data['Size'];
      this.aseet.ID = data['Id'];
      var owner = JSON.parse(data['Owner'])
      this.aseet.Owner = owner.user;
    }

  }
  ngOnInit() {

  }

  onSubmit() {
    console.log(this.form.value);
    this.httpOptions = {
      headers: new HttpHeaders({
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*'

      })
    }
    if (this.data) {
      var request = { "Color": this.form.value.Color, "AppraisedValue": this.form.value.AppraisedValue, "ID": this.data.ID, "Size": this.form.value.Size, "Owner": { "org": "Org1MSP", "user": this.form.value.Owner } };
      this._http.post<any>(URLS.UPDATE, JSON.stringify(request), this.httpOptions).subscribe((data: any) => {
        console.log(data);
       var response = { "Color": this.form.value.Color, "AppraisedValue": this.form.value.AppraisedValue, "ID": this.data.ID, "Size": this.form.value.Size, "Owner": JSON.stringify({ "org": "Org1MSP", "user": this.form.value.Owner }) };
        this.dialogRef.close(response)
      })
    }else{
      this._http.post<any>(URLS.CREATE, JSON.stringify(this.form.value), this.httpOptions).subscribe((data: any) => {
        console.log(data);
        var response = { "Color": this.form.value.Color, "AppraisedValue": this.form.value.AppraisedValue, "ID": data.AssetId, "Size": this.form.value.Size, "Owner": JSON.stringify({ "org": "Org1MSP", "user": this.form.value.Owner }) };
        this.dialogRef.close(response)
      })
    }
   
  }
  close() {
    this.dialogRef.close();
  }
}