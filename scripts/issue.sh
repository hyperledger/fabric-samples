#Script to issue tokens to a specified user in FabToken ecosystem

# Print help message

function printHelp() {
  echo "Script to issue tokens to a specific user."
  echo " "
  echo "Usage: "
  echo "  issue.sh <mode> [<RECEIVER_CONFIG_FILE> <ISSUER_MSP> <TRANSACTION_CHANNEL> <TOKEN_TYPE> <QUANTITY> <RECEIVER_MSP>]"
  echo "   Write the arguments in the order above. If the user wishes, he/she can substitute some of then by"
  echo "   environment variables created when executing set_env.sh script."
  echo "   In this example, <TRANSACTION_CHANNEL> should be mychannel."
  echo "  "
}

MODE=$1
RECEIVER_CONFIG_FILE=$2
ISSUER_MSP=$3
TRANSACTION_CHANNEL=$4
TOKEN_TYPE=$5
QUANTITY=$6
RECEIVER_MSP=$7

if [ "$MODE" == "exec" ]; then
    token issue --config $RECEIVER_CONFIG_FILE --mspPath $ISSUER_MSP --channel $TRANSACTION_CHANNEL --type $TOKEN_TYPE --quantity $QUANTITY --recipient $RECEIVER_MSP

else
    printHelp
    exit 1

fi