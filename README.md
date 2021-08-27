# Deploying WordPress website to the k8s cluster

------------------------------------------------
Example project to deploy WP to k8s cluster

------------------------------------------------
## Plan for deployment

1. Deploy k8s cluster on AWS with KOPS
2. Build MySQL and Wordpress custom images and make security scans locally
3. Push built images to the Docker Images repo
4. Pull kubectl to the local Jenkins agent
5. Pull created kube config to the local Jenkins agent
6. Create RBAC-things for administrative accounts
7. Deploy Pods/Services/Secrets/ConfigMaps/PVCs on the AWS kubernetes cluster
9. Update AWS Route53 hosted zones for the deployed apps





--------------------------------------------------
(c) Vadim Poyaskov, 2021
