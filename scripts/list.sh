#Script to list tokens of a specified user in FabToken ecosystem

#Print help message

function printHelp() {
  echo "Script to list the tokens owned by a specific user."
  echo "Usage: "
  echo "  issue.sh <mode> [<TARGET_USER_CONFIG_FILE> <TARGET_USER_MSP> <TRANSACTION_CHANNEL>]"
  echo "   Write the arguments in the order above. If the user wishes, he/she can substitute some of then by"
  echo "   environment variables created when executing set_env.sh script."
  echo "   In this example, <TRANSACTION_CHANNEL> should be mychannel."
  echo "  "
}

MODE=$1
TARGET_USER_CONFIG_FILE=$2
TARGET_USER_MSP=$3
TRANSACTION_CHANNEL=$4

if [ "$MODE" == "exec" ]; then
    token list --config $TARGET_USER_CONFIG_FILE --mspPath $TARGET_USER_MSP --channel $TRANSACTION_CHANNEL

else
    printHelp
    exit 1

fi