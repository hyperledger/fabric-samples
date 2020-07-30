/*
SPDX-License-Identifier: Apache-2.0
*/

package org.example;

import org.junit.Test;

public class TestApp {

	/*
	* Enrolls an admin
	* Registers a user
	* Initializes the ledger with some assets
	*/
	@Test
	public void start() throws Exception {
		EnrollAdmin.main(null);
		RegisterUser.main(null);
		ClientApp.main(null);
	}

	// Creates asset7, modify the parameters to create other assets
	@Test
	public void CreateAsset() throws Exception {
		ClientApp.CreateAsset("asset7", "magenta", "20", "kenysha", "800");
	}

	// Reads asset1, modify the parameters to read other assets
	@Test
	public void ReadAsset() throws Exception {
		ClientApp.ReadAsset("asset7");
	}

	// Updates asset3, modify the parameters to read other assets
	@Test
	public void UpdateAsset() throws Exception {
		ClientApp.UpdateAsset("asset3", "blue", "20", "Jin Soo", "600");
	}

	// Deletes asset2, modify the parameters to delete other assets
	@Test
	public void DeleteAsset() throws Exception {
		ClientApp.DeleteAsset("asset2");
	}

	// Transfers asset1 to arturo, modify the parameters to transfer other assets
	@Test
	public void TransferAsset() throws Exception {
		ClientApp.TransferAsset("asset1", "arturo");
	}
	// Outputs all the assets
	@Test
	public void GetAllAssets() throws Exception {
		ClientApp.GetAllAssets();
	}
}
