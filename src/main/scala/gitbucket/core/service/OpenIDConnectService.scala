package gitbucket.core.service

import java.net.URI

import com.nimbusds.jose.JWSAlgorithm.Family
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jose.{JOSEException, JWSAlgorithm}
import com.nimbusds.oauth2.sdk._
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.id.{ClientID, Issuer, State}
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator
import com.nimbusds.openid.connect.sdk.{AuthenticationErrorResponse, _}
import net.minidev.json.JSONArray
import gitbucket.core.model.Account
import gitbucket.core.model.Profile.profile.blockingApi._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.{asScalaSet, mapAsJavaMap}

/**
 * Service class for the OpenID Connect authentication.
 */
trait OpenIDConnectService {
  self: AccountFederationService =>

  private val logger = LoggerFactory.getLogger(classOf[OpenIDConnectService])

  private val JWK_REQUEST_TIMEOUT = 5000

  private val OIDC_SCOPE = new Scope(OIDCScopeValue.OPENID, OIDCScopeValue.EMAIL, OIDCScopeValue.PROFILE)

  /**
   * Obtain the OIDC metadata from discovery and create an authentication request.
   *
   * @param issuer      Issuer, used to construct the discovery endpoint URL, e.g. https://accounts.google.com
   * @param clientID    Client ID (given by the issuer)
   * @param redirectURI Redirect URI
   * @return Authentication request
   */
  def createOIDCAuthenticationRequest(issuer: Issuer, clientID: ClientID, redirectURI: URI): AuthenticationRequest = {
    val metadata = OIDCProviderMetadata.resolve(issuer)
    new AuthenticationRequest(
      metadata.getAuthorizationEndpointURI,
      new ResponseType(ResponseType.Value.CODE),
      OIDC_SCOPE,
      clientID,
      redirectURI,
      new State(),
      new Nonce()
    )
  }

  def syncOIDCremoveGroups(
    userName: String,
    groups: JSONArray,
    trygrp: String
  )(implicit s: Session) : Unit = {
    var isManager = AccountService.isUserGroupMember(trygrp,userName)
    //~ logger.info(s"user: ${userName} grp: ${trygrp} isManage: ${isManager} ${isManager.getClass.getName}")
    if (isManager.size == 0) {
      return
    }
    var oidc_grp = if (isManager(0)) {
      s"${trygrp}-admin"
    } else {
      s"${trygrp}-user"
    }
    //~ logger.info(s"trygrp=${trygrp} oidc_grp=${oidc_grp}")
    if (!groups.contains(oidc_grp)) {
      logger.info(s"Removing ${userName} from ${trygrp} group")
      AccountService.removeUserFromGroup(trygrp,userName)
    }
  }
  def syncOIDCaddGroups(
    userName: String,
    groupName:  String
  )(implicit s: Session) : Unit = {
    //~ logger.info(s"GROUP(${groupName}): ${userName}")
    var gbGrpName = ""
    var gbIsManager = false
    if (groupName.endsWith("-user")) {
      gbGrpName = groupName.substring(0,groupName.length()-5)
      gbIsManager = false
    } else if (groupName.endsWith("-admin")) {
      gbGrpName = groupName.substring(0,groupName.length()-6)
      gbIsManager = true
    } else {
      /* Do nothing! */
      return
    }
    //~ logger.info(s"gbGrpName: ${gbGrpName} admin: ${gbIsManager}")
    if (!AccountService.isGroupAccount(gbGrpName)) {
      return
    }
    var isMember = AccountService.isUserGroupMember(gbGrpName,userName)
    if (isMember.size != 0) {
      /* Already member... */
      return
    }
    logger.info(s"Adding ${userName} to ${gbGrpName} (Manager: ${gbIsManager}")
    AccountService.addUserToGroup(gbGrpName,userName,gbIsManager)
  }
  /**
   * If "groups" claim is found, we synchronize groups.
   * 
   * @param userName User being synchronized
   * @param groups   List of groups being claimed
   */
  def syncOIDCgroups(
    userName: String,
    groups: JSONArray
  )(implicit s: Session) : Unit = {
    /* First process member removals... */
    var cgrps = AccountService.getGroupsByUserName(userName)
    for (i <- 0 until cgrps.length) {
      //~ logger.info(s"i=${i} grp: ${cgrps(i)}")
      syncOIDCremoveGroups(userName, groups, cgrps(i))
    }
    /* Now process member additions... */
    for (i <- 0 until groups.size) {
      syncOIDCaddGroups(userName, groups.get(i).asInstanceOf[String])
    }
  }

  /**
   * Proceed the OpenID Connect authentication.
   *
   * @param params      Query parameters of the authentication response
   * @param redirectURI Redirect URI
   * @param state       State saved in the session
   * @param nonce       Nonce saved in the session
   * @param oidc        OIDC settings
   * @return ID token
   */
  def authenticate(
    params: Map[String, String],
    redirectURI: URI,
    state: State,
    nonce: Nonce,
    oidc: SystemSettingsService.OIDC
  )(implicit s: Session): Option[Account] =
    validateOIDCAuthenticationResponse(params, state, redirectURI) flatMap { authenticationResponse =>
      obtainOIDCToken(authenticationResponse.getAuthorizationCode, nonce, redirectURI, oidc) flatMap { claims =>
        Seq("email", "preferred_username", "name").map(k => Option(claims.getStringClaim(k))) match {
          case Seq(Some(email), preferredUsername, name) =>
            val res = getOrCreateFederatedUser(
              claims.getIssuer.getValue,
              claims.getSubject.getValue,
              email,
              preferredUsername,
              name
            )
            if (claims.getClaim("groups") != null) {
	      logger.info(s"Sync groups for ${res.get.userName}: ${claims.getClaim("groups")}")
	      syncOIDCgroups(res.get.userName, claims.getClaim("groups").asInstanceOf[JSONArray])
	    }
            return res
          case _ =>
            logger.info(s"OIDC ID token must have an email claim: claims=${claims.toJSONObject}")
            None
        }
      }
    }

  /**
   * Validate the authentication response.
   *
   * @param params      Query parameters of the authentication response
   * @param state       State saved in the session
   * @param redirectURI Redirect URI
   * @return Authentication response
   */
  def validateOIDCAuthenticationResponse(
    params: Map[String, String],
    state: State,
    redirectURI: URI
  ): Option[AuthenticationSuccessResponse] =
    try {
      AuthenticationResponseParser.parse(redirectURI, mapAsJavaMap(params)) match {
        case response: AuthenticationSuccessResponse =>
          if (response.getState == state) {
            Some(response)
          } else {
            logger.info(s"OIDC authentication state did not match: response(${response.getState}) != session($state)")
            None
          }
        case response: AuthenticationErrorResponse =>
          logger.info(s"OIDC authentication response has error: ${response.getErrorObject}")
          None
      }
    } catch {
      case e: ParseException =>
        logger.info(s"OIDC authentication response has error: $e")
        None
    }

  /**
   * Obtain the ID token from the OpenID Provider.
   *
   * @param authorizationCode Authorization code in the query string
   * @param nonce             Nonce
   * @param redirectURI       Redirect URI
   * @param oidc              OIDC settings
   * @return Token response
   */
  def obtainOIDCToken(
    authorizationCode: AuthorizationCode,
    nonce: Nonce,
    redirectURI: URI,
    oidc: SystemSettingsService.OIDC
  ): Option[IDTokenClaimsSet] = {
    val metadata = OIDCProviderMetadata.resolve(oidc.issuer)
    val tokenRequest = new TokenRequest(
      metadata.getTokenEndpointURI,
      new ClientSecretBasic(oidc.clientID, oidc.clientSecret),
      new AuthorizationCodeGrant(authorizationCode, redirectURI),
      OIDC_SCOPE
    )
    val httpResponse = tokenRequest.toHTTPRequest.send()
    try {
      OIDCTokenResponseParser.parse(httpResponse) match {
        case response: OIDCTokenResponse =>
          validateOIDCTokenResponse(response, metadata, nonce, oidc)
        case response: TokenErrorResponse =>
          logger.info(s"OIDC token response has error: ${response.getErrorObject.toJSONObject}")
          None
      }
    } catch {
      case e: ParseException =>
        logger.info(s"OIDC token response has error: $e")
        None
    }
  }

  /**
   * Validate the token response.
   *
   * @param response Token response
   * @param metadata OpenID Provider metadata
   * @param nonce    Nonce
   * @return Claims
   */
  def validateOIDCTokenResponse(
    response: OIDCTokenResponse,
    metadata: OIDCProviderMetadata,
    nonce: Nonce,
    oidc: SystemSettingsService.OIDC
  ): Option[IDTokenClaimsSet] =
    Option(response.getOIDCTokens.getIDToken) match {
      case Some(jwt) =>
        val validator = oidc.jwsAlgorithm map { jwsAlgorithm =>
          new IDTokenValidator(
            metadata.getIssuer,
            oidc.clientID,
            jwsAlgorithm,
            metadata.getJWKSetURI.toURL,
            new DefaultResourceRetriever(JWK_REQUEST_TIMEOUT, JWK_REQUEST_TIMEOUT)
          )
        } getOrElse {
          new IDTokenValidator(metadata.getIssuer, oidc.clientID)
        }
        try {
          Some(validator.validate(jwt, nonce))
        } catch {
          case e @ (_: BadJOSEException | _: JOSEException) =>
            logger.info(s"OIDC ID token has error: $e")
            None
        }
      case None =>
        logger.info(s"OIDC token response does not have a valid ID token: ${response.toJSONObject}")
        None
    }
}

object OpenIDConnectService {

  /**
   * All signature algorithms.
   */
  val JWS_ALGORITHMS: Map[String, Set[JWSAlgorithm]] = Seq(
    "HMAC" -> Family.HMAC_SHA,
    "RSA" -> Family.RSA,
    "ECDSA" -> Family.EC,
    "EdDSA" -> Family.ED
  ).toMap.map { case (name, family) => (name, asScalaSet(family).toSet) }
}
