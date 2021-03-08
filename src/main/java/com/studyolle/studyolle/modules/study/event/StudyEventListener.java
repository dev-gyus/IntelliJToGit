package com.studyolle.studyolle.modules.study.event;

import com.studyolle.studyolle.infra.config.AppProperties;
import com.studyolle.studyolle.infra.mail.EmailMessage;
import com.studyolle.studyolle.infra.mail.EmailService;
import com.studyolle.studyolle.modules.account.Account;
import com.studyolle.studyolle.modules.account.AccountPredicates;
import com.studyolle.studyolle.modules.account.AccountRepository;
import com.studyolle.studyolle.modules.notification.Notification;
import com.studyolle.studyolle.modules.notification.NotificationRepository;
import com.studyolle.studyolle.modules.notification.NotificationType;
import com.studyolle.studyolle.modules.study.Study;
import com.studyolle.studyolle.modules.study.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;


@Slf4j
@Async
@Component
@Transactional
@RequiredArgsConstructor
public class StudyEventListener {
    private final StudyRepository studyRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;        // Email 보내기위한 클래스
    private final TemplateEngine templateEngine;    // html 만들기용 타임리프 클래스
    private final AppProperties appProperties;      // URL Root context주소 가져오기위한 클래스
    private final NotificationRepository notificationRepository;

    @EventListener
    public void handleStudyCreatedEvent(StudyCreatedEvent studyCreatedEvent){
        Study study = studyRepository.findStudyWithTagsAndZonesById(studyCreatedEvent.getStudy().getId());
        Iterable<Account> accounts = accountRepository.findAll(AccountPredicates.findByTagsAndZones(study.getTags(), study.getZones()));
        accounts.forEach(account -> {
            if(account.isStudyCreatedByEmail()){

                sendStudyCreateEmail(study, account, "새로운 스터디가 생겼습니다",
                        "스터디올레, '" + study.getTitle() + "' 스터디가 생겼습니다.");
            }
            if(account.isStudyCreatedByWeb()){
                saveStudyCreatedNotification(study, account, study.getShortDescription(), NotificationType.STUDY_CREATED);
            }
        });
    }

    @EventListener
    public void handleStudyUpdateEvent(StudyUpdateEvent studyUpdateEvent){
        Study study = studyRepository.findStudyManagersMembersById(studyUpdateEvent.getStudy().getId());
        Set<Account> accounts = new HashSet<>();
        accounts.addAll(study.getManagers());
        accounts.addAll(study.getMembers());

        accounts.forEach(account -> {
            if(account.isStudyCreatedByEmail()){
                sendStudyCreateEmail(study, account, studyUpdateEvent.getMessage()
                        , "스터디올레, '" + study.getTitle() + "' 에 새소식이 있습니다.");
            }
            if(account.isStudyCreatedByWeb()){
                saveStudyCreatedNotification(study, account, studyUpdateEvent.getMessage(), NotificationType.STUDY_UPDATE);
            }
        });
    }

    private void saveStudyCreatedNotification(Study study, Account account, String message, NotificationType notificationType) {
        Notification notification = new Notification();
        notification.setTitle(study.getTitle());
        notification.setLink("/study/" + URLEncoder.encode(study.getPath(),StandardCharsets.UTF_8));
        notification.setCreateLocalDateTime(LocalDateTime.now());
        notification.setMessage(message);
        notification.setAccount(account);
        notification.setNotificationType(notificationType);
        notificationRepository.save(notification);
    }

    private void sendStudyCreateEmail(Study study, Account account, String contextMessage, String subject) {
        Context context = new Context();
        context.setVariable("link", "/study/" + URLEncoder.encode(study.getPath(), StandardCharsets.UTF_8));
        context.setVariable("nickname", account.getNickname());
        context.setVariable("linkName", study.getTitle());
        context.setVariable("message", contextMessage);
        context.setVariable("host", appProperties.getHost());
        String message = templateEngine.process("email/simple-link", context);

        EmailMessage emailMessage = EmailMessage.builder()
                .subject(subject)
                .to(account.getEmail())
                .message(message)
                .build();

        emailService.sendEmail(emailMessage);
    }
}
