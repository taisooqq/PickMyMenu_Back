package com.pmm.pickmymenu_back.service;

import com.pmm.pickmymenu_back.domain.Member;
import com.pmm.pickmymenu_back.dto.request.member.MemberJoinReq;
import com.pmm.pickmymenu_back.dto.request.member.MemberLoginReq;
import com.pmm.pickmymenu_back.dto.request.member.MemberUpdateReq;
import com.pmm.pickmymenu_back.dto.response.member.MemberEmailCheckRes;
import com.pmm.pickmymenu_back.dto.response.member.MemberLoginRes;
import com.pmm.pickmymenu_back.dto.response.member.MemberMyPageRes;
import com.pmm.pickmymenu_back.exception.MemberException;
import com.pmm.pickmymenu_back.repository.MemberRepository;
import com.pmm.pickmymenu_back.util.JWTUtil;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JWTUtil jwtUtil;


    // 회원가입 로직
    public String joinProcess(MemberJoinReq req) {

        boolean isEmailExist = memberRepository.findByEmail(req.getEmail()).isPresent();
        if (isEmailExist) throw new IllegalArgumentException("이미 등록된 이메일입니다.");

        boolean isPhoneNumberExist = memberRepository.findByPhoneNumber(req.getPhoneNumber()).isPresent();
        if (isPhoneNumberExist) throw new IllegalArgumentException("이미 등록된 전화번호입니다.");

        req.setPassword(bCryptPasswordEncoder.encode(req.getPassword()));
        Member member = Member.create(req);
        memberRepository.save(member);
        return "회원가입 성공";
    }

    // 로그인 로직
    public MemberLoginRes loginProcess(MemberLoginReq req, HttpServletResponse res) {
        Member member = memberRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        if (!bCryptPasswordEncoder.matches(req.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("잘못된 비밀번호입니다.");
        }

        String token = jwtUtil.generateToken(member.getEmail(), member.getName());
        res.addHeader("Set-Cookie",
                String.format("token=%s; HttpOnly; Path=/", token));

        return new MemberLoginRes(token, member.getName());

    }

    // 마이페이지 조회
    public MemberMyPageRes getMemberInfo(String token) {
        if (token == null) throw new MemberException("토큰이 존재하지 않습니다.");

        String email = jwtUtil.validateAndExtract(token);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException("해당 사용자를 찾을 수 없습니다."));

        return new MemberMyPageRes(member);
    }

    // 수정페이지 가기 전 비밀번호 확인
    public boolean verifyPassword(String token, String password) {
        if (token == null) throw new MemberException("토큰이 존재하지 않습니다.");

        String email = jwtUtil.validateAndExtract(token); // `token`에서 `email` 추출
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException("해당 사용자를 찾을 수 없습니다."));

        return bCryptPasswordEncoder.matches(password, member.getPassword()); // 비밀번호 확인
    }

    // 이메일 중복 확인 메서드
    public MemberEmailCheckRes isEmailExist(String email) {
        String normalizedEmail = email.trim().toLowerCase(); // 이메일 정규화
        Optional<Member> existEmail = memberRepository.findByEmail(normalizedEmail);
        if (existEmail.isPresent()) {
            throw new MemberException("이미 등록된 이메일입니다.");
        }
        return new MemberEmailCheckRes("사용 가능한 이메일입니다.");
    }

    // 회원 정보 수정
    public boolean updateMember(MemberUpdateReq memberUpdateReq, String token) {
        if (token == null) throw new MemberException("토큰이 존재하지 않습니다.");

        String email = jwtUtil.validateAndExtract(token); // `token`에서 `email` 추출
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException("해당 사용자를 찾을 수 없습니다."));

        // 전화번호가 변경되었을 때만 중복 확인을 하도록
        if (!member.getPhoneNumber().equals(memberUpdateReq.getPhoneNumber())) {
            boolean isPhoneNumberExist = memberRepository.findByPhoneNumber(memberUpdateReq.getPhoneNumber()).isPresent();
            if (isPhoneNumberExist) {
                throw new MemberException("이미 등록된 전화번호입니다.");
            }
        }

        // 회원 정보 업데이트
        member.updateMember(
                member.getName(), // 이름은 수정하지 않으므로 기존 이름 그대로
                memberUpdateReq.getPhoneNumber(),
                (memberUpdateReq.getPassword() == null || memberUpdateReq.getPassword().isEmpty()) ?
                        member.getPassword() : bCryptPasswordEncoder.encode(memberUpdateReq.getPassword()) // 비밀번호 수정이 있을 때만 변경
        );

        // 저장
        memberRepository.save(member);
        return true; // 업데이트 성공
    }

    public boolean checkPhoneNumber(String phoneNumber) {
        Optional<Member> existingMember = memberRepository.findByPhoneNumber(phoneNumber);
        return !existingMember.isPresent();  // 전화번호가 없다면 사용 가능
    }


}
