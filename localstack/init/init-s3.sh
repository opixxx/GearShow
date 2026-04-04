#!/bin/bash
# LocalStack 시작 시 S3 버킷을 자동 생성한다.
awslocal s3 mb s3://gearshow-images --region ap-northeast-2
echo "S3 버킷 생성 완료: gearshow-images"
