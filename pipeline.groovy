#!/usr/bin/groovy

String ocpApiServer = env.OCP_API_SERVER ? "${env.OCP_API_SERVER}" : "https://openshift.default.svc.cluster.local"

node('master') {

  env.NAMESPACE = readFile('/var/run/secrets/kubernetes.io/serviceaccount/namespace').trim()
  env.TOKEN = readFile('/var/run/secrets/kubernetes.io/serviceaccount/token').trim()
  env.OC_CMD = "oc --token=${env.TOKEN} --server=${ocpApiServer} --certificate-authority=/run/secrets/kubernetes.io/serviceaccount/ca.crt --namespace=${env.NAMESPACE}"

  env.APP_NAME = "${env.JOB_NAME}".replaceAll(/-?pipeline-?/, '').replaceAll(/-?${env.NAMESPACE}-?/, '')
  def projectBase = "${env.NAMESPACE}".replaceAll(/-dev/, '')
  env.STAGE1 = "${projectBase}-dev"
  env.STAGE2 = "${projectBase}-val"
  env.STAGE3 = "${projectBase}-prod"

//  sh(returnStdout: true, script: "${env.OC_CMD} get is jenkins-slave-image-mgmt --template=\'{{ .status.dockerImageRepository }}\' -n openshift > /tmp/jenkins-slave-image-mgmt.out")
//  env.SKOPEO_SLAVE_IMAGE = readFile('/tmp/jenkins-slave-image-mgmt.out').trim()
//  println "${env.SKOPEO_SLAVE_IMAGE}"

}

node {
  stage('SCM Checkout') {
    checkout scm
    //sh "orig=\$(pwd); cd \$(dirname ${pomFileLocation}); git describe --tags; cd \$orig"
  }
  stage('Build Image') {
    sh """
      ${env.OC_CMD} get builds
      ${env.OC_CMD} start-build ${env.APP_NAME} --wait=true || exit 1
      ${env.OC_CMD} get builds
    """
  }
    
}

node {
  stage('Verify deployment to ${env.STAGE1}')     
  openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE1}", verifyReplicacount: true)
  input "Promote application to val?"
}
node {
  stage('Promote to ${env.STAGE2}')
}

node {
  stage('Verify deployment to ${env.STAGE2}')
  openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE1}", verifyReplicacount: true)
}

node {
  echo 'Promote to prod'
}
