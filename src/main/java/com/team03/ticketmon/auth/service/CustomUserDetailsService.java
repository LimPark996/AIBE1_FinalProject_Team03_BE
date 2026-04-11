package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * CustomUserDetailsService — 로컬 로그인용 UserDetails 조회 서비스
 *
 * username으로 UserEntity를 찾아 CustomUserDetails(userId, username, nickname, password, role 권한)로 매핑한다.
 * LoginFilter -> AuthenticationManager 플로우에서 호출되며,
 * 사용자가 없으면 UsernameNotFoundException을 던진다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private  final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("유저 정보가 없습니다."));
        
        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getRoleName()))
        );
    }
}
