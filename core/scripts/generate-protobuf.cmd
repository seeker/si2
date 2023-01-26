mkdir target\generated-sources\protobuf\
protoc --proto_path=src\main\resources\protobuf\ src\main\resources\protobuf\file_load.proto src\main\resources\protobuf\image_path.proto src\main\resources\protobuf\db_update.proto src\main\resources\protobuf\node_command.proto --java_out=target\generated-sources\protobuf\
