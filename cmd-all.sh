#!/bin/bash

for vm in 'vault' 'consul' 'mongodb' 'rabbitmq' 'nomad_server' 'nomad_client'
do
vagrant $1 $vm&
done
