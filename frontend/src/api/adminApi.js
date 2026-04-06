import api from './axios';

export const getAllUsers = async () => {
    const response = await api.get('/admin/users');
    return response.data;
};

export const updateUserStatus = async (userId, status) => {
    const response = await api.put(`/admin/users/${userId}/status?status=${status}`);
    return response.data;
};

export const updateUserRole = async (userId, role) => {
    const response = await api.put(`/admin/users/${userId}/role?role=${role}`);
    return response.data;
};

export const deleteUser = async (userId) => {
    const response = await api.post(`/admin/users/${userId}/delete`);
    return response.data;
};
