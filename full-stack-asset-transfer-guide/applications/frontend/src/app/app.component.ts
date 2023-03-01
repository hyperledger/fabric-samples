import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { AssetDialogComponent } from './asset-dialog/asset-dialog.component';
import { URLS } from './urls';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {

  @ViewChild(MatSort, { static: false }) sort!: MatSort ;
  @ViewChild(MatPaginator, { static: true }) paginator!: MatPaginator;
  dataSource = new MatTableDataSource();
  httpOptions;
  title = 'frontend';
  assets = [];
  displayedColumns: String[] = ["position", "id",'owner','color','appraisedValue',"size" ,'transfer','edit','delete'];
  constructor(private _http: HttpClient, private dialog: MatDialog,private snackBar:MatSnackBar) {
    this.httpOptions = {
      headers: new HttpHeaders({
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*'

      })
    }
    this._http.get<any>(URLS.LIST,this.httpOptions).subscribe(data => {
      this.dataSource.data = data;
    })
  }
  ngOnInit() {
    
 }
  editRow(asset:any,index:number){
    const dialogRef = this.dialog.open(AssetDialogComponent, {
      width: '500px', height: '100vh',position:{right:'0'},data:asset

    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.dataSource.data[index]=(result)
        this.dataSource._updateChangeSubscription();
      }
    });
  }
  delete(asset:any,index:number){
    this._http.post<any>(URLS.DELETE,JSON.stringify({"id":asset.ID}),this.httpOptions).subscribe((data:any) => {
      console.log(data);
      this.dataSource.data.splice(index,1)
      this.dataSource._updateChangeSubscription();
      this.snackBar.open(asset.ID+ ' deleted', '', {duration:1000
      });
       })
  }
  addNewAsset(){
    const dialogRef = this.dialog.open(AssetDialogComponent, {
      width: '500px', height: '100vh',position:{right:'0'},

    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.dataSource.data.push(result)
        this.dataSource._updateChangeSubscription();
      }
    });
  }
  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
   }
   ngAfterViewInit() {
    console.log('after ininti');
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }
}
