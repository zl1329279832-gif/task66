const base = {
    get() {
        const contextPath = '/tongchengshangmenweiliuxitong';
        const origin = window.location.protocol + '//' + window.location.host;
        return {
            url : origin + contextPath + '/',
            name: "tongchengshangmenweiliuxitong",
            // 退出到首页链接
            indexUrl: origin + contextPath + '/front/index.html'
        };
    },
    getProjectName(){
        return {
            projectName: "同城上门喂遛宠物系统"
        }
    }
}
export default base
