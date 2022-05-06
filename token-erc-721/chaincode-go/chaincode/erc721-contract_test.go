package chaincode

import (
	"encoding/base64"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/hyperledger/fabric-protos-go/ledger/queryresult"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

const owner = "x509::CN=minter,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
const operator = "x509::CN=org,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=AR"

type MockStub struct {
	shim.ChaincodeStubInterface
	mock.Mock
}

func (ms *MockStub) GetStateByPartialCompositeKey(objectType string, keys []string) (shim.StateQueryIteratorInterface, error) {
	args := ms.Called(objectType, keys)
	return args.Get(0).(shim.StateQueryIteratorInterface), args.Error(1)
}

func (ms *MockStub) GetState(key string) ([]byte, error) {
	args := ms.Called(key)
	return args.Get(0).([]byte), args.Error(1)
}

func (ms *MockStub) PutState(key string, value []byte) error {
	args := ms.Called(key, value)
	return args.Error(0)
}
func (ms *MockStub) SetEvent(key string, value []byte) error {
	args := ms.Called(key, value)
	return args.Error(0)
}

func (ms *MockStub) DelState(key string) error {
	args := ms.Called(key)
	return args.Error(0)
}

func (ms *MockStub) CreateCompositeKey(objectType string, attributes []string) (string, error) {
	args := ms.Called(objectType, attributes)
	return args.Get(0).(string), args.Error(1)
}

type MockClientIdentity struct {
	cid.ClientIdentity
	mock.Mock
}

func (mci *MockClientIdentity) GetID() (string, error) {
	args := mci.Called()
	return args.Get(0).(string), args.Error(1)
}

func (mci *MockClientIdentity) GetMSPID() (string, error) {
	args := mci.Called()
	return args.Get(0).(string), args.Error(1)
}

func (mc *MockContext) GetStub() shim.ChaincodeStubInterface {
	args := mc.Called()
	return args.Get(0).(*MockStub)
}

type MockContext struct {
	contractapi.TransactionContextInterface
	mock.Mock
}

func (mc *MockContext) GetClientIdentity() cid.ClientIdentity {
	args := mc.Called()
	return args.Get(0).(*MockClientIdentity)
}

type MockIterator struct {
	shim.StateQueryIteratorInterface
	queryresult.KV
}

func (it *MockIterator) HasNext() bool {
	return false
}

func setupStub() (*MockContext, *MockStub) {
	balancePrefix := "balance"
	approvalPrefix := "approval"
	nftPrefix := "nft"
	mockTokenId := "101"
	anyString := mock.AnythingOfType("string")
	anyUint8Slice := mock.AnythingOfType("[]uint8")
	nftStr := "{\"tokenId\":\"101\",\"owner\":\"" + owner + "\",\"tokenURI\":\"https://example.com/nft101.json\",\"approved\":\"" + operator + "\"}"
	approvalStr := "{\"owner\":\"" + owner + "\",\"operator\":\"" + owner + "\",\"approved\":true}"

	ms := new(MockStub)
	iterator := new(MockIterator)

	ms.On("GetStateByPartialCompositeKey", balancePrefix, []string{owner}).Return(iterator, nil)
	ms.On("GetStateByPartialCompositeKey", nftPrefix, []string{}).Return(iterator, nil)

	ms.On("CreateCompositeKey", nftPrefix, []string{mockTokenId}).Return("nft101", nil)
	ms.On("CreateCompositeKey", nftPrefix, []string{"102"}).Return("nft102", nil)
	ms.On("CreateCompositeKey", approvalPrefix, []string{owner, owner}).Return(approvalPrefix+owner+owner, nil)
	ms.On("CreateCompositeKey", approvalPrefix, []string{owner, operator}).Return(approvalPrefix+owner+operator, nil)
	ms.On("CreateCompositeKey", balancePrefix, []string{owner, mockTokenId}).Return(balancePrefix+owner+mockTokenId, nil)
	ms.On("CreateCompositeKey", balancePrefix, []string{operator, mockTokenId}).Return(balancePrefix+operator+mockTokenId, nil)
	ms.On("CreateCompositeKey", balancePrefix, []string{owner, "102"}).Return(balancePrefix+owner+mockTokenId, nil)

	ms.On("GetState", "nft101").Return([]byte(nftStr), nil)
	ms.On("GetState", "nft102").Return([]uint8{}, nil)
	ms.On("GetState", approvalPrefix+owner+owner).Return([]byte(approvalStr), nil)
	ms.On("GetState", "name").Return([]byte("lala"), nil)
	ms.On("GetState", "symbol").Return([]byte("lelo"), nil)

	ms.On("PutState", "name", []byte("someName")).Return(nil)
	ms.On("PutState", "symbol", []byte("someSymbol")).Return(nil)
	ms.On("PutState", anyString, anyUint8Slice).Return(nil)
	ms.On("PutState", balancePrefix+owner+"101", []byte{0}).Return(nil)
	ms.On("PutState", balancePrefix+owner+"102", []byte{'\u0000'}).Return(nil)
	ms.On("PutState", "nft101", []byte("nft101")).Return(nil)
	ms.On("PutState", "nft102", []byte("nft102")).Return(nil)

	ms.On("SetEvent", "ApprovalForAll", anyUint8Slice).Return(nil)
	ms.On("SetEvent", "Transfer", anyUint8Slice).Return(nil)

	ms.On("DelState", anyString).Return(nil)

	mci := new(MockClientIdentity)
	owner64 := base64.StdEncoding.EncodeToString([]byte(owner))
	operator64 := base64.StdEncoding.EncodeToString([]byte(owner))

	mci.On("GetID").Return(owner64, nil)
	mci.On("GetID").Return(operator64, nil)
	mci.On("GetMSPID").Return("Org1MSP", nil)

	mc := new(MockContext)
	mc.On("GetStub").Return(ms)
	mc.On("GetClientIdentity").Return(mci)
	return mc, ms
}

func TestBalanceOf(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	balance := c.BalanceOf(ctx, owner)
	assert.Equal(t, 0, balance)

}
func TestTotalSupply(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)
	totalNft := c.TotalSupply(ctx)
	assert.Equal(t, 0, totalNft)

}

func TestOwnerOf(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	owner, _ := c.OwnerOf(ctx, "101")
	assert.Equal(t, owner, owner)

}

func TestApprove(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	approved, _ := c.Approve(ctx, "", "101")
	assert.Equal(t, true, approved)

}

func TestSetApprovalForAll(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	appAll, _ := c.SetApprovalForAll(ctx, operator, true)
	assert.Equal(t, true, appAll)

}

func TestIsApprovedForAll(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	isApp, _ := c.SetApprovalForAll(ctx, operator, true)
	assert.Equal(t, true, isApp)

}

func TestGetApproved(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	getApp, _ := c.GetApproved(ctx, "101")
	assert.Equal(t, ""+operator+"", getApp)
}

func TestTransferFrom(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	transfer, _ := c.TransferFrom(ctx, owner, operator, "101")

	assert.Equal(t, true, transfer)
}

func TestName(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	name, _ := c.Name(ctx)

	assert.Equal(t, "lala", name)
}

func TestSymbol(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	symbol, _ := c.Symbol(ctx)

	assert.Equal(t, "lelo", symbol)
}

func TestTokenURI(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	tokenURI, _ := c.TokenURI(ctx, "101")

	assert.Equal(t, "https://example.com/nft101.json", tokenURI)
}

func TestInitialize(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	option, _ := c.Initialize(ctx, "someName", "someSymbol")
	assert.Equal(t, true, option)
}

func TestMintWithTokenURI(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	mint, _ := c.MintWithTokenURI(ctx, "102", "https://example.com/nft102.json")

	nft := new(Nft)
	nft.Owner = owner
	nft.TokenId = "102"
	nft.TokenURI = "https://example.com/nft102.json"

	assert.Equal(t, nft.Owner, mint.Owner)
	assert.Equal(t, nft, mint)

}

func TestBurn(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)

	burn, _ := c.Burn(ctx, "101")
	assert.Equal(t, true, burn)
}

func TestClientAccoundId(t *testing.T) {
	ctx, _ := setupStub()
	c := new(TokenERC721Contract)
	client, _ := c.ClientAccountID(ctx)
	assert.Equal(t, owner, client)
}
