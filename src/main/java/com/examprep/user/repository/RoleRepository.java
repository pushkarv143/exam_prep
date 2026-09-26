package com.examprep.user.repository;

import com.examprep.user.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(String name);

    List<Role> findByNameIn(Collection<String> names);

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r order by r.system desc, r.id")
    List<Role> findAllWithPermissions();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsByName(String name);

    @Query(value = "select r.name, count(ur.user_id) from roles r left join user_roles ur on ur.role_id = r.id "
            + "group by r.name", nativeQuery = true)
    List<Object[]> countUsersByRole();
}
