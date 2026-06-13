export const getImgUrl = (imgName) => {
    if (!imgName) return '';
    return `${import.meta.env.VITE_API_BASE_URL}/upload/${imgName}`;
};