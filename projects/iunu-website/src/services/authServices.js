const authServices = {
    login: async(loginData) => {
        console.log("Login API:", loginData);

        // Temporary mock response

        return {
            success: true,
            user: {
                email: loginData.email,
            },
            token: "temporary-token",
        };
    },

    register: async(registerData) => {
        console.log("Register API:", registerData);

        return {
            success: true,
            message: "Account created successfully.",
        };
    },

    forgotPassword: async(email) => {
        console.log("Forgot Password API:", email);

        return {
            success: true,
            message: "If this email exists, a reset link will be sent.",
        };
    },

    logout: () => {
        localStorage.removeItem("isLoggedIn");
        localStorage.removeItem("userEmail");
    },
};

export default authServices;