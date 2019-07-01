#Script to redeem tokens from a specified user in FabToken

#Print help message

function printHelp() {
  echo "Script to redeem tokens to a specific user."
  echo " "
  echo "Usage: "
  echo "  issue.sh <mode> [<TARGET_USER> <TARGET_USER_MSP> <TRANSACTION_CHANNEL> <REMOVABLE_TOKEN_ID> <QUANTITY>]"
  echo "   Write the arguments in the order above. If the user wishes, he/she can substitute some of then by"
  echo "   environment variables created when executing set_env.sh script."
  echo "   In this example, <TRANSACTION_CHANNEL> should be mychannel."
  echo "  "
}

MODE=$1
TARGET_USER_CONFIG_FILE=$2
TARGET_USER_MSP=$3
TRANSACTION_CHANNEL=$4
REMOVABLE_TOKEN_ID=$5
QUANTITY=$6

if [ "$MODE" == "exec" ]; then
    token redeem --config $TARGET_USER_CONFIG_FILE --mspPath $TARGET_USER_MSP --channel $TRANSACTION_CHANNEL  --tokenIDs $REMOVABLE_TOKEN_ID --quantity $QUANTITY
else
    printHelp
    exit 1

fi



