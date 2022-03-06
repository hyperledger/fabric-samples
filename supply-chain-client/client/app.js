// SPDX-License-Identifier: Apache-2.0

'use strict';

var app = angular.module('application', []);

// Angular Controller
app.controller('appController', function($scope, appFactory){

	$("#success_holder").hide();
	$("#success_create").hide();
	$("#error_holder").hide();
	$("#error_query").hide();
	
	$scope.queryAllProduce = function(){

		appFactory.queryAllProduce(function(data){
			$scope.all_produce = data;
		});
	}

	$scope.queryProduce = function(){

		var id = $scope.produce_id;

		appFactory.queryProduce(id, function(data){
			$scope.query_produce = data;

			if ($scope.query_produce == "Could not locate produce"){
				console.log()
				$("#error_query").show();
			} else{
				$("#error_query").hide();
			}
		});
	}

	$scope.recordProduce = function(){

		appFactory.recordProduce($scope.produce, function(data){
			
			$scope.create_produce = data;
			$("#success_create").show();
		});
	}

	$scope.changeHolder = function(){

		appFactory.changeHolder($scope.holder, function(data){
			$scope.change_holder = data;
			if ($scope.change_holder == "Error: no produce found"){
				$("#error_holder").show();
				$("#success_holder").hide();
			} else{
				$("#success_holder").show();
				$("#error_holder").hide();
			}
		});
	}

});

// Angular Factory
app.factory('appFactory', function($http){
	
	var factory = {};

    factory.queryAllProduce = function(callback){

    	$http.get('/get_all_produce/').success(function(output){
			callback(output)
		});
	}

	factory.queryProduce = function(id, callback){
    	$http.get('/get_produce/'+id).success(function(output){
			callback(output)
		});
	}

	factory.recordProduce = function(data, callback){
		console.log(data)
		var produce = data.assetID + "-" + data.color + "-" + data.size + "-" + data.appraisedValue + "-" + data.owner;

    	$http.get('/add_produce/'+produce).success(function(output){
			callback(output)
		});
	}

	factory.changeHolder = function(data, callback){

		var holder = data.assetID + "-" + data.name;

    	$http.get('/change_holder/'+holder).success(function(output){
			callback(output)
		});
	}

	return factory;
});


