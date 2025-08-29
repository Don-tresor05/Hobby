package backend.hobbiebackend.service.impl;

import backend.hobbiebackend.handler.NotFoundException;
import backend.hobbiebackend.model.dto.AppClientSignUpDto;
import backend.hobbiebackend.model.dto.BusinessRegisterDto;
import backend.hobbiebackend.model.entities.*;
import backend.hobbiebackend.model.entities.enums.GenderEnum;
import backend.hobbiebackend.model.entities.enums.UserRoleEnum;
import backend.hobbiebackend.model.repostiory.AppClientRepository;
import backend.hobbiebackend.model.repostiory.BusinessOwnerRepository;
import backend.hobbiebackend.model.repostiory.UserRepository;
import backend.hobbiebackend.model.repostiory.UserRoleRepository;
import backend.hobbiebackend.service.UserRoleService;
import backend.hobbiebackend.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final AppClientRepository appClientRepository;
    private final BusinessOwnerRepository businessOwnerRepository;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(ModelMapper modelMapper, UserRepository userRepository,
                           AppClientRepository appClientRepository,
                           BusinessOwnerRepository businessOwnerRepository, UserRoleService userRoleService, PasswordEncoder passwordEncoder) {
        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
        this.appClientRepository = appClientRepository;
        this.businessOwnerRepository = businessOwnerRepository;
        this.userRoleService = userRoleService;
        this.passwordEncoder = passwordEncoder;

    }

    @Override
public List<UserEntity> seedUsersAndUserRoles() {
    List<UserEntity> seededUsers = new ArrayList<>();

    // Seed ADMIN user (only if no users exist)
    if (userRepository.count() == 0) {
        // Save roles first (if they don't exist)
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setRole(UserRoleEnum.USER);
        userRole = userRoleService.saveRole(userRole);

        UserRoleEntity businessRole = new UserRoleEntity();
        businessRole.setRole(UserRoleEnum.BUSINESS_USER);
        businessRole = userRoleService.saveRole(businessRole);

        UserRoleEntity adminRole = new UserRoleEntity();
        adminRole.setRole(UserRoleEnum.ADMIN);
        adminRole = userRoleService.saveRole(adminRole);

        // Create ADMIN user
        AppClient admin = new AppClient();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPassword(passwordEncoder.encode("admin123")); // Use a strong password in production!
        admin.setRoles(List.of(adminRole)); // Assign ADMIN role
        admin.setFullName("Admin User");
        admin.setGender(GenderEnum.OTHER); // Or any default gender

        appClientRepository.save(admin);
        seededUsers.add(admin);

        // Create default USER (optional)
        AppClient user = new AppClient();
        user.setUsername("user");
        user.setEmail("user@example.com");
        user.setPassword(passwordEncoder.encode("user123"));
        user.setRoles(List.of(userRole));
        user.setFullName("Regular User");
        user.setGender(GenderEnum.MALE);

        appClientRepository.save(user);
        seededUsers.add(user);

        // Create default BUSINESS_USER (optional)
        BusinessOwner business = new BusinessOwner();
        business.setUsername("business");
        business.setEmail("business@example.com");
        business.setPassword(passwordEncoder.encode("business123"));
        business.setRoles(List.of(businessRole));
        business.setBusinessName("Test Business");
        business.setAddress("123 Business St");

        businessOwnerRepository.save(business);
        seededUsers.add(business);
    }

    return seededUsers;
}

    @Override
    public AppClient register(AppClientSignUpDto user) {
        UserRoleEntity userRole = this.userRoleService.getUserRoleByEnumName(UserRoleEnum.USER);
        AppClient appClient = this.modelMapper.map(user, AppClient.class);
        appClient.setRoles(List.of(userRole));
        appClient.setPassword(this.passwordEncoder.encode(user.getPassword()));
        return appClientRepository.save(appClient);
    }

    @Override
    public BusinessOwner registerBusiness(BusinessRegisterDto business) {
        UserRoleEntity businessUserRole = this.userRoleService.getUserRoleByEnumName(UserRoleEnum.BUSINESS_USER);
        BusinessOwner businessOwner = this.modelMapper.map(business, BusinessOwner.class);
        businessOwner.setRoles(List.of(businessUserRole));
        businessOwner.setPassword(this.passwordEncoder.encode(business.getPassword()));
        return businessOwnerRepository.save(businessOwner);
    }

    @Override
    public BusinessOwner saveUpdatedUser(BusinessOwner businessOwner) {
        return this.businessOwnerRepository.save(businessOwner);
    }

    @Override
    public AppClient saveUpdatedUserClient(AppClient appClient) {
        return this.appClientRepository.save(appClient);
    }

    @Override
    public UserEntity findUserById(Long userId) {
        Optional<UserEntity> byId = this.userRepository.findById(userId);
        if (byId.isPresent()) {
            return byId.get();
        } else {
            throw new NotFoundException("User not found");
        }
    }

    @Override
    public UserEntity findUserByEmail(String email) {
        Optional<UserEntity> byEmail = this.userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            return byEmail.get();
        } else {
            return null;
        }
    }

    @Override
    public BusinessOwner findBusinessOwnerById(Long id) {
        Optional<BusinessOwner> businessOwner = this.businessOwnerRepository.findById(id);
        if (businessOwner.isPresent()) {
            return businessOwner.get();
        } else {
            throw new NotFoundException("Can not find business owner");
        }
    }

    @Override
    public UserEntity findUserByUsername(String username) {
        Optional<UserEntity> byUsername = this.userRepository.findByUsername(username);
        if (byUsername.isPresent()) {
            return byUsername.get();
        } else {
            throw new NotFoundException("Can not find user with this username");
        }
    }

    @Override
    public boolean userExists(String username, String email) {
        Optional<UserEntity> byUsername = this.userRepository.findByUsername(username);
        Optional<UserEntity> byEmail = this.userRepository.findByEmail(email);
        return byUsername.isPresent() || byEmail.isPresent();
    }

    @Override
    public void saveUserWithUpdatedPassword(UserEntity userEntity) {
        this.userRepository.save(userEntity);
    }

    @Override
    public boolean deleteUser(Long id) {
        UserEntity user = findUserById(id);
        if (user == null) {
            return false;
        }
        Optional<BusinessOwner> byId = this.businessOwnerRepository.findById(user.getId());

        if (byId.isPresent()) {
            List<AppClient> all = appClientRepository.findAll();
            for (AppClient client : all) {
                for (Hobby hobby : byId.get().getHobby_offers()) {
                    client.getHobby_matches().remove(hobby);
                    client.getSaved_hobbies().remove(hobby);
                }
                this.userRepository.save(client);
            }
        }
        userRepository.delete(user);
        return true;
    }


    @Override
    public AppClient findAppClientById(Long clientId) {
        Optional<AppClient> user = this.appClientRepository.findById(clientId);
        if (user.isPresent()) {

            return user.get();
        } else {
            throw new NotFoundException("Can not find current user.");
        }
    }

    @Override
    public void findAndRemoveHobbyFromClientsRecords(Hobby hobby) {
        List<AppClient> all = this.appClientRepository.findAll();

        for (AppClient appClient : all) {
            appClient.getSaved_hobbies().remove(hobby);
            appClient.getHobby_matches().remove(hobby);
        }
    }


    @Override
    public boolean businessExists(String businessName) {
        Optional<BusinessOwner> byBusinessName = this.businessOwnerRepository.findByBusinessName(businessName);
        return byBusinessName.isPresent();
    }

    @Override
    public AppClient findAppClientByUsername(String username) {
        return this.appClientRepository.findByUsername(username).orElseThrow();
    }

    @Override
    public BusinessOwner findBusinessByUsername(String username) {
        return this.businessOwnerRepository.findByUsername(username).get();
    }
}
