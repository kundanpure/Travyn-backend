package com.travyn.safety.service;

import com.travyn.common.exception.ResourceNotFoundException;
import com.travyn.safety.dto.CreateEmergencyContactRequest;
import com.travyn.safety.dto.EmergencyContactDTO;
import com.travyn.safety.entity.EmergencyContact;
import com.travyn.safety.repository.EmergencyContactRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmergencyContactService {

    private final EmergencyContactRepository emergencyContactRepository;
    private final ModelMapper modelMapper;

    @Transactional(readOnly = true)
    public List<EmergencyContactDTO> getMyContacts(UUID userId) {
        return emergencyContactRepository.findByUserId(userId).stream()
                .map(contact -> {
                    EmergencyContactDTO dto = modelMapper.map(contact, EmergencyContactDTO.class);
                    dto.setTelegramConnected(contact.getTelegramChatId() != null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public EmergencyContactDTO addContact(UUID userId, CreateEmergencyContactRequest request) {
        if (emergencyContactRepository.countByUserId(userId) >= 5) {
            throw new IllegalStateException("Maximum of 5 emergency contacts allowed");
        }

        EmergencyContact contact = EmergencyContact.builder()
                .userId(userId)
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .relationship(request.getRelationship())
                .build();

        contact = emergencyContactRepository.save(contact);
        return modelMapper.map(contact, EmergencyContactDTO.class);
    }

    @Transactional
    public void deleteContact(UUID userId, UUID contactId) {
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found"));

        if (!contact.getUserId().equals(userId)) {
            throw new IllegalStateException("Unauthorized to delete this contact");
        }

        emergencyContactRepository.delete(contact);
    }
}
