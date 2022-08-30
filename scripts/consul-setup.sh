#!/bin/sh
# Seed the Consul KV store with data

consul kv put config/general/required-hashes "SHA-256,SHA-512"
consul kv put config/fileloader/load-rate-limit "50"
consul kv put config/general/thumbnail-size "300"
consul kv put config/mongodb/database/integration "mongodbintegration"
consul kv put config/mongodb/database/si2 "si2"
consul kv put config/rabbitmq/vhost 127.0.0.1
