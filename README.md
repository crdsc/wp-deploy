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


Demo Web-application an be deployed to the AWS or on-Prem Kubernetes Cluster.


There is the CI/CD pipoeline on the Jenkins.


This project is parameterized. Jenkinsfile is here: [Jenkinsfile.groovy](Jenkinsfile.groovy)


Deployed to the on-prem k8s cluster application can be acccessible here [resulta.itman.today](https://resulta.itman.today/)


AWS Kubernetes Cluster deployed application living here: [result.crdsmartcity.org](http://result.crdsmartcity.org.)




--------------------------------------------------
(c) Vadim Poyaskov, 2021
