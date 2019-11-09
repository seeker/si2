#!/bin/bash

for vm in 'vault' 'consul' 'mongodb' 'rabbitmq'
do
vagrant $1 $vm&
done
