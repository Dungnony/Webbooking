package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    /** The guest read the conversation: everything not written by them is now read. */
    @Modifying
    @Query("update ChatMessage m set m.readAt = :now where m.room.id = :roomId and m.readAt is null and m.sender.id <> :senderId")
    int markReadExceptSender(@Param("roomId") Long roomId, @Param("senderId") Long senderId, @Param("now") LocalDateTime now);

    /** An admin read the conversation: the guest's messages are now read (admin replies stay unread for the guest). */
    @Modifying
    @Query("update ChatMessage m set m.readAt = :now where m.room.id = :roomId and m.readAt is null and m.sender.id = :senderId")
    int markReadFromSender(@Param("roomId") Long roomId, @Param("senderId") Long senderId, @Param("now") LocalDateTime now);

    @Query("select count(m) from ChatMessage m where m.room.user.id = :userId and m.readAt is null and m.sender.id <> :userId")
    long countUnreadForOwner(@Param("userId") Long userId);

    @Query("select count(m) from ChatMessage m where m.readAt is null and m.sender = m.room.user")
    long countUnreadForAdmins();
}
