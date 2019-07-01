#Script to make transfers in FabToken ecosystem

#Input variables

#Print help message
function printHelp() {
  echo "Script to transfer tokens from a specific user to another."
  echo " "
  echo "Usage: "
  echo "  issue.sh <mode> [<SENDER_CONFIG_FILE> <SENDER_MSP> <TRANSACTION_CHANNEL> <INPUT_TOKEN_ID> <SHARES_FILE_PATH>]"
  echo "   Write the arguments in the order above. If the user wishes, he/she can substitute some of then by"
  echo "   environment variables created when executing set_env.sh script."
  echo "   In this example, <TRANSACTION_CHANNEL> should be mychannel."
  echo "  "
}
MODE=$1
SENDER_CONFIG_FILE=$2
SENDER_MSP=$3
TRANSACTION_CHANNEL=$4
INPUT_TOKEN_ID=$5
SHARES_FILE_PATH=$6

if [ "$MODE" == "exec" ]; then
    token transfer --config $SENDER_CONFIG_FILE --mspPath $SENDER_MSP --channel $TRANSACTION_CHANNEL --tokenIDs $INPUT_TOKEN_ID --shares $SHARES_FILE_PATH
else
    printHelp
    exit 1

fi



