package com.library.service.impl;

import com.library.dto.LoginRequest;
import com.library.dto.LoginResponse;
import com.library.dto.RegisterRequest;
import com.library.entity.Reader;
import com.library.entity.User;
import com.library.entity.enums.Gender;
import com.library.entity.enums.UserRole;
import com.library.exception.ApiException;
import com.library.repository.ReaderRepository;
import com.library.repository.UserRepository;
import com.library.security.JwtProvider;
import com.library.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ReaderRepository readerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthServiceImpl(UserRepository userRepository,
                           ReaderRepository readerRepository,
                           PasswordEncoder passwordEncoder,
                           JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.readerRepository = readerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ApiException("账号或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException("账号或密码错误");
        }

        String token = jwtProvider.generateToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRole(), user.getReaderId());
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String studentId = request.getStudentId();

        if (userRepository.existsByUsername(studentId)) {
            throw new ApiException("该账号已注册");
        }

        // 1. Create Reader (profile only, no password)
        Reader reader = new Reader();
        reader.setStudentId(studentId);
        reader.setName(request.getName());
        reader.setDepartment(request.getDepartment());
        reader.setBirthDate(request.getBirthDate());
        reader.setPhone(request.getPhone());
        if (request.getGender() != null && !request.getGender().isEmpty()) {
            reader.setGender(Gender.valueOf(request.getGender()));
        }
        reader = readerRepository.save(reader);

        // 2. Create User (auth, linked to Reader)
        User user = new User();
        user.setUsername(studentId);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.READER);
        user.setReaderId(reader.getId());
        user = userRepository.save(user);

        // 3. Issue JWT
        String token = jwtProvider.generateToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRole(), user.getReaderId());
    }
}
