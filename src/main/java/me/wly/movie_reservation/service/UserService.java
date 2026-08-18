package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.mapper.UserMapper;
import me.wly.movie_reservation.model.dto.UserRegisterDTO;
import me.wly.movie_reservation.model.entity.User;
import me.wly.movie_reservation.model.enum_class.UserType;
import me.wly.movie_reservation.model.vo.UserVO;
import me.wly.movie_reservation.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Generate a normal customer user.
     * Raise 400 if neither email nor phone number is passed.
     */
    public UserVO createCustomerUser(UserRegisterDTO newUser) {
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
        created.setUserType(UserType.CUSTOMER);
        userRepository.save(created);
        return userMapper.entityToVO(created);
    }

    private String encodePassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    private boolean verifyPassword(String rawPassword,String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
