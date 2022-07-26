
ENV_DAL=`echo $DISCOVERY_AS_LOCALHOST`

echo "ENV_DAL:"$DISCOVERY_AS_LOCALHOST

if [ "$ENV_DAL" != "true" ]
then
	export DISCOVERY_AS_LOCALHOST=true
fi

echo "DISCOVERY_AS_LOCALHOST="$DISCOVERY_AS_LOCALHOST

echo "run fabcar..."

go run fabcar.go
