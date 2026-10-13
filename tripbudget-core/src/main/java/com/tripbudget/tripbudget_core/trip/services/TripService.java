package com.tripbudget.tripbudget_core.trip.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.trip.dtos.request.CreateTripRequest;
import com.tripbudget.tripbudget_core.trip.dtos.request.GetAllTripsRequest;
import com.tripbudget.tripbudget_core.trip.dtos.request.InitialMemberRequest;
import com.tripbudget.tripbudget_core.trip.dtos.request.TripFilterRequest;
import com.tripbudget.tripbudget_core.trip.dtos.request.UpdateShareSettingsRequest;
import com.tripbudget.tripbudget_core.trip.dtos.request.UpdateTripRequest;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import com.tripbudget.tripbudget_core.trip.dtos.response.TripResponse;
import com.tripbudget.tripbudget_core.trip.dtos.response.TripShareResponse;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberRole;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.trip.enums.TripVisibility;
import com.tripbudget.tripbudget_core.trip.events.PublicTripChangedEvent;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.specifications.TripSpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service()
@Transactional(readOnly = true)
public class TripService {
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final HashidsService hashidsService;
    private final ApplicationEventPublisher eventPublisher;
    private final TripCoverService tripCoverService;

    public TripService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            HashidsService hashidsService,
            ApplicationEventPublisher eventPublisher,
            TripCoverService tripCoverService
    ) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.hashidsService = hashidsService;
        this.eventPublisher = eventPublisher;
        this.tripCoverService = tripCoverService;
    }

    @Transactional
    public TripResponse createTrip(Long currentUserId, CreateTripRequest dto){
            validateCreateRequest(dto);

            TripEntity trip = TripEntity.create(
                    currentUserId,
                    dto.getName(),
                    dto.getDestination(),
                    dto.getDescription(),
                    dto.getStartDate(),
                    dto.getEndDate(),
                    dto.getBaseCurrency()
            );
            TripEntity savedTrip = tripRepository.save(trip);

            TripMemberEntity tripOwnerMember = TripMemberEntity.createOwner(savedTrip, currentUserId);

            // set OWNER
            tripMemberRepository.save(tripOwnerMember);

            // add member if have
            List<InitialMemberRequest> initialMemberRequestList =
                    dto.getInitialMembers() == null
                            ? List.of()
                            : dto.getInitialMembers();

            Set<Long> invitedUsers = new HashSet<>();

            for(InitialMemberRequest item: initialMemberRequestList) {
                Long memberUserId = hashidsService.decode(item.userId());
                if(memberUserId.equals(currentUserId)){
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Trip owner must not be added again as a member"
                    );
                }
                if(!invitedUsers.add(memberUserId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "A user can be invited only once"
                    );
                }
                if (item.role() == TripMemberRole.OWNER) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Inviting member cannot have OWNER role"
                    );
                }
            }

            List<TripMemberEntity> invitations = initialMemberRequestList.stream()
                    .map(item -> TripMemberEntity
                            .invite(savedTrip, hashidsService.decode(item.userId()), item.role())).toList();

            tripMemberRepository.saveAll(invitations);

            return TripResponse.from(savedTrip, hashidsService);
    }

    public PageResponse<TripResponse> getMyTrips(
            Long currentUserId,
            int page,
            int size,
            TripFilterRequest filter
    ) {
        Specification<TripEntity> spec = TripSpecifications.buildSpecification(currentUserId, filter);
        Sort sort = TripSpecifications.buildSort(
                filter != null ? filter.sortBy() : null,
                filter != null ? filter.sortDirection() : null
        );

        Page<TripEntity> tripPage = tripRepository.findAll(spec, PageRequest.of(page, size, sort));

        Page<TripResponse> responsePage = tripPage.map(t -> TripResponse.from(t, hashidsService));

        return PageResponse.from(responsePage);
    }

    public TripResponse getTripById(Long tripId, Long currentUserId)
    {
        TripEntity trip = this.getActiveMemberTrip(tripId, currentUserId);
        return TripResponse.from(trip, hashidsService);
    }

    @Transactional
    public TripResponse update(Long currentUserId, Long tripId, UpdateTripRequest dto)
    {
        TripEntity trip = this.getActiveMemberTrip(tripId, currentUserId);

        if (!trip.getOwnerId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the trip owner can update this trip"
            );
        }

        if(dto.getEndDate() != null && dto.getStartDate() != null && dto.getEndDate().isBefore(dto.getStartDate()))
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "End date must be on or after start date"
            );
        }

        if (dto.getStatus() == TripStatus.DELETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot set trip status to DELETED via update. Use delete trip endpoint instead."
            );
        }

        tripCoverService.updateCover(currentUserId, trip, dto.getCoverUploadId(), dto.isRemoveCover());

        trip.update(
                dto.getName(),
                dto.getDescription(),
                dto.getDestination(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getBaseCurrency(),
                dto.getStatus()
        );

        eventPublisher.publishEvent(PublicTripChangedEvent.of(tripId));
        return TripResponse.from(trip, hashidsService);
    }

    @Transactional
    public void deleteTrip(
            Long currentUserId,
            Long tripId
    ) {
        TripEntity trip = getActiveMemberTrip(
                tripId,
                currentUserId
        );

        if (!trip.getOwnerId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the trip owner can delete this trip"
            );
        }

        trip.deleteTrip();
        eventPublisher.publishEvent(new PublicTripChangedEvent(tripId, trip.getShareToken()));
//        tripRepository.delete(trip);
    }

    private TripEntity getActiveMemberTrip(
            Long tripId,
            Long currentUserId
    ) {
        TripEntity trip = tripRepository.findActiveTrip(tripId);

        if(trip == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Trip not found"
            );
        }

        boolean isActiveMember =
                tripMemberRepository.existsByTrip_IdAndUserIdAndStatus(
                        tripId,
                        currentUserId,
                        TripMemberStatus.ACTIVE
                );

        if (!isActiveMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this trip"
            );
        }

        return trip;
    }

    public TripShareResponse getShareSettings(Long currentUserId, Long tripId) {
        TripEntity trip = getActiveMemberTrip(tripId, currentUserId);
        return new TripShareResponse(
                hashidsService.encode(trip.getId()),
                trip.getVisibility(),
                trip.getPublicRole(),
                trip.getShareToken()
        );
    }

    @Transactional
    public TripShareResponse updateShareSettings(
            Long currentUserId,
            Long tripId,
            UpdateShareSettingsRequest dto
    ) {
        TripEntity trip = getActiveMemberTrip(tripId, currentUserId);

        TripMemberEntity caller = tripMemberRepository.findByTrip_IdAndUserIdAndIsDelFalse(tripId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission in this trip"));

        if (caller.getRole() != TripMemberRole.OWNER && caller.getRole() != TripMemberRole.VICE && caller.getRole() != TripMemberRole.EDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only trip owner and vice can update share settings");
        }

        TripMemberRole role = dto.publicRole() != null ? dto.publicRole() : TripMemberRole.VIEWER;
        trip.updateShareSettings(dto.visibility(), role);
        eventPublisher.publishEvent(PublicTripChangedEvent.of(tripId));

        return new TripShareResponse(
                hashidsService.encode(trip.getId()),
                trip.getVisibility(),
                trip.getPublicRole(),
                trip.getShareToken()
        );
    }

    @Transactional
    public TripShareResponse regenerateShareToken(Long currentUserId, Long tripId) {
        TripEntity trip = getActiveMemberTrip(tripId, currentUserId);

        TripMemberEntity caller = tripMemberRepository.findByTrip_IdAndUserIdAndIsDelFalse(tripId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission in this trip"));

        if (caller.getRole() != TripMemberRole.OWNER && caller.getRole() != TripMemberRole.VICE && caller.getRole() != TripMemberRole.EDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only trip owner and vice can regenerate share link");
        }

        String oldShareToken = trip.getShareToken();
        trip.regenerateShareToken();
        eventPublisher.publishEvent(new PublicTripChangedEvent(tripId, oldShareToken));

        return new TripShareResponse(
                hashidsService.encode(trip.getId()),
                trip.getVisibility(),
                trip.getPublicRole(),
                trip.getShareToken()
        );
    }

    public TripMemberStatus getPublicMemberStatus(String shareToken, Long currentUserId) {
        TripEntity trip = tripRepository.findByShareTokenAndIsDelFalse(shareToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found or link has expired"));

        if (trip.getVisibility() != TripVisibility.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This trip is private. Only members can view it.");
        }

        if (currentUserId.equals(trip.getOwnerId())) {
            return TripMemberStatus.ACTIVE;
        }

        return tripMemberRepository.findByTrip_IdAndUserIdAndIsDelFalse(trip.getId(), currentUserId)
                .map(TripMemberEntity::getStatus)
                .orElse(null);
    }

    @Transactional
    public TripResponse joinPublicTrip(String shareToken, Long currentUserId) {
        TripEntity trip = tripRepository.findByShareTokenAndIsDelFalse(shareToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found or link has expired"));

        if (trip.getVisibility() != TripVisibility.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This trip is private. Only members can join.");
        }

        java.util.Optional<TripMemberEntity> existingMemberOpt = tripMemberRepository.findByTrip_IdAndUserId(trip.getId(), currentUserId);
        TripMemberRole assignRole = trip.getPublicRole() != null ? trip.getPublicRole() : TripMemberRole.VIEWER;

        if (existingMemberOpt.isEmpty()) {
            // When joining via the link, set status INVITED so the trip owner can approve it
            TripMemberEntity newMember = TripMemberEntity.invite(trip, currentUserId, assignRole);
            tripMemberRepository.save(newMember);
        } else {
            TripMemberEntity existing = existingMemberOpt.get();
            if (existing.isDel() || existing.getStatus() == TripMemberStatus.LEFT || existing.getStatus() == TripMemberStatus.REMOVED) {
                existing.reactivate(assignRole, TripMemberStatus.INVITED);
                tripMemberRepository.save(existing);
            }
        }

        return TripResponse.from(trip, hashidsService);
    }

    private void validateCreateRequest(CreateTripRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "End date must be on or after start date"
            );
        }
    }
}
