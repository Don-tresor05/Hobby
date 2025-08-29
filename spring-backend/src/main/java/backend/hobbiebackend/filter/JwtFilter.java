package backend.hobbiebackend.filter;

import backend.hobbiebackend.security.HobbieUserDetailsService;
import backend.hobbiebackend.utility.JWTUtility;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);
    
    @Autowired
    private JWTUtility jwtUtility;

    @Autowired
    private HobbieUserDetailsService hobbieUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, 
                                   HttpServletResponse httpServletResponse, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String authorization = httpServletRequest.getHeader("Authorization");
        String token = null;
        String userName = null;

        if (null != authorization && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
            logger.debug("Extracted token from Authorization header");
            
            try {
                userName = jwtUtility.getUsernameFromToken(token);
                logger.debug("Extracted username: {}", userName);
            } catch (SignatureException e) {
                logger.error("Invalid JWT signature for token: {}", token.substring(0, Math.min(20, token.length())) + "...");
                // Don't process further, but continue the filter chain
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            } catch (ExpiredJwtException e) {
                logger.error("JWT token has expired");
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            } catch (MalformedJwtException e) {
                logger.error("JWT token is malformed");
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            } catch (Exception e) {
                logger.error("Error parsing JWT token: {}", e.getMessage());
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }
        }

        if (null != userName && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = hobbieUserDetailsService.loadUserByUsername(userName);

                if (jwtUtility.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken
                            = new UsernamePasswordAuthenticationToken(userDetails,
                            null, userDetails.getAuthorities());

                    usernamePasswordAuthenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(httpServletRequest)
                    );

                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    logger.debug("Successfully authenticated user: {}", userName);
                } else {
                    logger.warn("JWT token validation failed for user: {}", userName);
                }
            } catch (Exception e) {
                logger.error("Error during authentication: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}