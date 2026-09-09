package me.wly.movie_reservation.user;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.common.security.JwtUtil;
import me.wly.movie_reservation.user.dto.UserLoginDTO;
import me.wly.movie_reservation.user.dto.UserRegisterDTO;
import me.wly.movie_reservation.user.model.User;
import me.wly.movie_reservation.user.model.UserRole;
import me.wly.movie_reservation.user.vo.UserLoginVO;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * Generate a normal customer user.
     * Raise 400 if neither email nor phone number is passed.
     */
    public UserLoginVO customerCreate(UserRegisterDTO newUser) {
        boolean hasEmail = StringUtils.hasText(newUser.emailAddress());
        boolean hasPhoneNumber = StringUtils.hasText(newUser.phoneNumber());
        if(!hasEmail && !hasPhoneNumber) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        User created = new User();

        if(userRepository.existsByEmailAddress(newUser.emailAddress())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Email Address exists");
        }
        else created.setEmailAddress(newUser.emailAddress());

        if(userRepository.existsByPhoneNumber(newUser.phoneNumber())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Phone Number exists");
        }
        else created.setPhoneNumber(newUser.phoneNumber());
        created.setUsername(newUser.username());
        created.setNickname(newUser.nickname());
        created.setPassword(encodePassword(newUser.password()));
        created.setUserRole(UserRole.CUSTOMER);
        userRepository.save(created);
        return userMapper.entityToVO(created);
    }

    public JwtUtil.TokenResult authenticateAndGetToken(UserLoginDTO userLoginDTO){
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginDTO.username(),userLoginDTO.password()));
        return jwtUtil.generateToken(authentication.getName());
    }

    public UserLoginVO getUserInfo(UserLoginDTO userLoginDTO) {
        User user = userRepository.findByUsername(userLoginDTO.username()).orElseThrow(()->new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));
        return userMapper.entityToVO(user);
    }


    private String encodePassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    private boolean verifyPassword(String rawPassword,String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
