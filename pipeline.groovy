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
}

node {
  stage('SCM Checkout') {
    checkout scm
  }
  stage('Build Image') {
    try {
      sh """
        ${env.OC_CMD} start-build ${env.APP_NAME} --wait=true || exit 1
      """
    } catch (err) {
      echo "Creating build"
      sh """
        ${env.OC_CMD} new-app ${env.STAGE1}/${env.APP_NAME} -n ${env.STAGE1}
      """
    }
  }
}

node {
  stage("Verify deployment to ${env.STAGE1}") {    
    try {
      openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE1}", verifyReplicacount: true)
      //input "Promote application to val?"
    } catch (err) {
      echo "Creating deployment config and route..."
      sh """
        ${env.OC_CMD} new-app ${env.STAGE1}/${env.APP_NAME}:latest -n ${env.STAGE1}
        ${env.OC_CMD} expose svc/cotd -n ${env.STAGE1}
      """
    }
  }
}

node {
  stage("Promote to ${env.STAGE2}") {
    sh """
    ${env.OC_CMD} tag ${env.STAGE1}/${env.APP_NAME}:latest ${env.STAGE2}/${env.APP_NAME}:latest
    """
  }
}

node {
  stage("Verify deployment to ${env.STAGE2}") {    
    try {
      openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE2}", verifyReplicacount: true)
      input "Promote application to val?"
    } catch (err) {
      echo "Creating deployment config and route..."
      sh """
        ${env.OC_CMD} new-app ${env.STAGE2}/${env.APP_NAME}:latest -n ${env.STAGE2}
        ${env.OC_CMD} expose svc/cotd -n ${env.STAGE2}
      """
    }
  }
}

node {
  stage("Promote to ${env.STAGE3}") {
    sh """
      ${env.OC_CMD} tag ${env.STAGE2}/${env.APP_NAME}:latest ${env.STAGE3}/${env.APP_NAME}:latest
    """ 
  }
}

node {
  stage("Verify deployment to ${env.STAGE3}") {
  try {
    openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE3}", verifyReplicacount: true)
  } catch (err) {
    echo "Creating deployment config and route..."
    sh """
      ${env.OC_CMD} new-app ${env.STAGE3}/${env.APP_NAME}:latest -n ${env.STAGE3}
      ${env.OC_CMD} expose svc/cotd -n ${env.STAGE3}
      """
    }
  }
}
